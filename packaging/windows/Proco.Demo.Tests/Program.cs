using System.Security.Cryptography;
using System.Text.Json;
using Proco.Demo;

if (args.Length == 3 && args[0] == "--render")
{
 var bundle = DemoManifest.Load(args[1]); var destination = Path.GetFullPath(args[2]);
 var session = new SessionStore(destination).Create(bundle.Version);
 File.WriteAllText(Path.Combine(destination, "compose.yml"), ComposeDocument.Render(session, bundle));
 File.WriteAllText(Path.Combine(destination, "verification-session.json"), JsonSerializer.Serialize(session));
 Console.WriteLine(session.Project); return;
}

var root = Path.Combine(Path.GetTempPath(), "proco-tests-" + Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(root);
int passed = 0;
void Assert(bool value, string name) { if (!value) throw new Exception(name); passed++; Console.WriteLine("PASS " + name); }
async Task Fails(Func<Task> action, string name) { try { await action(); } catch { passed++; Console.WriteLine("PASS " + name); return; } throw new Exception("Expected failure: " + name); }
var image = new ImageSpec("proco-demo:test", "sha256:" + new string('a', 64));
var m = new DemoManifest("1.0.0-test", new string('b', 40), "linux/amd64", new string('c', 64), image, image, image);
try
{
 var store = new SessionStore(root); var s = store.Create(m.Version); var second = store.Create(m.Version);
 Assert(s.Id != second.Id && s.Project != second.Project, "new session preserves distinct namespace");
 Assert(s.Password != second.Password && s.Password.Length == 48, "random database credentials per session");
 Assert(s.Url.Host == "127.0.0.1" && s.Port >= 1024, "localhost URL with persisted usable port");
 Assert(store.List().Count == 2, "creating a session retains previous records");
 store.Select(s); Assert(store.Selected(store.List())?.Id == s.Id, "selected session survives reload");
 Assert(store.List().Single(x => x.Id == s.Id).Password == s.Password, "credentials and port survive reload");
 var compose = ComposeDocument.Render(s, m);
 Assert(compose.Contains("pull_policy: never") && !compose.Contains("build:"), "runtime cannot build or pull images");
 Assert(compose.Split("ports:").Length == 2 && compose.Contains($"127.0.0.1:{s.Port}:80"), "only frontend port is published");
 Assert(compose.Contains("proco-data:/var/lib/postgresql/data") && !compose.Contains("container_name"), "Compose isolates persistent storage per project");
 await Fails(() => Task.Run(() => (s with { Id = "../../other" }).Validate()), "reject path traversal session");
 await Fails(() => Task.Run(() => (s with { Password = "' unsafe" }).Validate()), "reject YAML credential injection");
 await Fails(() => Task.Run(() => (s with { Port = 80 }).Validate()), "reject invalid persisted port");
 var manifestFile = Path.Combine(root, "manifest.json"); File.WriteAllText(manifestFile, JsonSerializer.Serialize(m));
 Assert(DemoManifest.Load(manifestFile) == m, "manifest roundtrip validates image identities");
 File.WriteAllText(manifestFile, JsonSerializer.Serialize(m with { Platform = "linux/arm64" }));
 await Fails(() => Task.Run(() => DemoManifest.Load(manifestFile)), "reject incompatible payload platform");
 var runner = new FakeRunner(image.Id); var probe = new FakeProbe();
 var runtime = new DockerRuntime("docker.exe", runner, probe, store, m, root);
 await runtime.Start(s, _ => { }, 1);
 Assert(runner.Calls.Any(a => a.Contains("--pull") && a.Contains("never")), "start explicitly forbids downloads");
 Assert(runner.Calls.Where(a => a.Contains("up")).All(a => a.Contains(s.Project)), "start targets only selected project");
 Assert(File.ReadAllText(runtime.ComposePath(s)) == compose, "start persists exact executable Compose configuration");
 await runtime.Stop(s);
 Assert(runner.Calls.Last().Contains("stop") && !runner.Calls.Any(a => a.Contains("down") || a.Contains("--volumes")), "stop never removes containers or volumes");
 var before = runner.Calls.Count;
 await Fails(() => runtime.Start(s with { PackageVersion = "older" }, _ => { }), "old package sessions require explicit matching installer");
 Assert(runner.Calls.Count == before, "version mismatch cannot mutate Docker");
 runner.Remote = true; await Fails(runtime.CheckEngine, "reject remote Docker endpoint"); runner.Remote = false;
 runner.Engine = "windows"; await Fails(runtime.CheckEngine, "reject Windows container mode"); runner.Engine = "linux";
 runner.FailUp = true; var probes = probe.Calls;
 await Fails(() => runtime.Start(s, _ => { }), "report occupied port or startup failure");
 Assert(probe.Calls == probes, "failed Compose cannot open unrelated service"); runner.FailUp = false;
 probe.Value = false; await Fails(() => runtime.Start(s, _ => { }, 1), "readiness failure does not report ready"); probe.Value = true;
 runner.MissingImages = true;
 File.WriteAllText(Path.Combine(root, "images.tar.gz"), "synthetic archive only for integrity test");
 await Fails(() => runtime.EnsureImages(_ => { }), "archive hash mismatch prevents loading");
 Assert(!runner.Calls.Any(a => a.Contains("load")), "corrupted payload never reaches Docker load");
 var digest = Convert.ToHexString(SHA256.HashData(File.ReadAllBytes(Path.Combine(root, "images.tar.gz")))).ToLowerInvariant();
 var validArchive = new DockerRuntime("docker.exe", runner, probe, store, m with { ArchiveSha256 = digest }, root);
 await validArchive.EnsureImages(_ => { }); Assert(runner.Calls.Any(a => a.Contains("load")), "valid missing images use bundled archive");
 runner.Leak = s.Password; var report = await runtime.Diagnostic(s); Assert(!report.Contains(s.Password), "diagnostic redacts database secret");
 File.WriteAllText(Path.Combine(root, "selected.txt"), "missing-session");
 await Fails(() => Task.Run(() => store.Selected(store.List())), "missing selected session never silently resets data");
 Console.WriteLine($"RESULT: {passed} checks passed");
}
finally { Directory.Delete(root, true); }

sealed class FakeProbe : IReadinessProbe { public int Calls; public bool Value = true; public Task<bool> Ready(Uri u) { Calls++; return Task.FromResult(Value); } }
sealed class FakeRunner(string imageId) : ICommandRunner
{
 public List<IReadOnlyList<string>> Calls = []; public bool Remote, FailUp, MissingImages; public string Engine = "linux", Leak = "";
 public Task<CommandResult> Run(string executable, IReadOnlyList<string> args, TimeSpan timeout)
 {
  Calls.Add(args.ToArray()); string text = ""; int code = 0;
  if (args.Contains("info")) text = Engine;
  if (args.Contains("context") && args.Contains("inspect")) text = Remote ? "ssh://elsewhere" : "npipe:////./pipe/dockerDesktopLinuxEngine";
  if (args.Contains("image") && args.Contains("inspect")) { text = imageId; if (MissingImages) code = 1; }
  if (args.Contains("load")) MissingImages = false;
  if (args.Contains("up") && FailUp) code = 1;
  if (args.Contains("logs")) text = Leak;
  return Task.FromResult(new CommandResult(code, text, ""));
 }
}
