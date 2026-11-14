using System.Diagnostics;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace Proco.Demo;

public sealed record CommandResult(int Code, string Output, string Error);
public interface ICommandRunner { Task<CommandResult> Run(string executable, IReadOnlyList<string> arguments, TimeSpan timeout); }
public sealed class ProcessRunner : ICommandRunner
{
    public async Task<CommandResult> Run(string executable, IReadOnlyList<string> arguments, TimeSpan timeout)
    {
        var start = new ProcessStartInfo(executable) { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
        foreach (var a in arguments) start.ArgumentList.Add(a);
        // Never inherit an unrelated remote Docker host or a Compose environment file.
        foreach (var variable in new[] { "DOCKER_HOST", "DOCKER_CONTEXT", "COMPOSE_FILE", "COMPOSE_PROJECT_NAME", "COMPOSE_ENV_FILES" }) start.Environment.Remove(variable);
        using var process = Process.Start(start) ?? throw new InvalidOperationException("No fue posible iniciar el componente local.");
        var output = process.StandardOutput.ReadToEndAsync(); var error = process.StandardError.ReadToEndAsync();
        using var cancellation = new CancellationTokenSource(timeout);
        try { await process.WaitForExitAsync(cancellation.Token); }
        catch (OperationCanceledException) { process.Kill(true); await process.WaitForExitAsync(); throw new TimeoutException("La operación tardó más de lo esperado. Pulsa Diagnóstico y contacta a soporte; los datos se conservan."); }
        return new(process.ExitCode, await output, await error);
    }
}

public interface IReadinessProbe { Task<bool> Ready(Uri url); }
public sealed class ReadinessProbe : IReadinessProbe
{
    readonly HttpClient client = new(new HttpClientHandler { UseProxy = false, AllowAutoRedirect = false }) { Timeout = TimeSpan.FromSeconds(3) };
    public async Task<bool> Ready(Uri url)
    {
        try
        {
            using var req = new HttpRequestMessage(HttpMethod.Get, new Uri(url, "api/me"));
            req.Headers.Authorization = new AuthenticationHeaderValue("Basic", Convert.ToBase64String(Encoding.UTF8.GetBytes("almacen:Simulacion123!")));
            using var response = await client.SendAsync(req);
            if (!response.IsSuccessStatusCode) return false;
            using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
            return json.RootElement.TryGetProperty("username", out var name) && name.GetString() == "almacen" && json.RootElement.TryGetProperty("role", out var role) && role.GetString() == "WAREHOUSE";
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or JsonException) { return false; }
    }
}

public sealed class DockerRuntime(string docker, ICommandRunner runner, IReadinessProbe probe, SessionStore sessions, DemoManifest manifest, string payload)
{
    public const string Context = "desktop-linux";
    public string ComposePath(DemoSession s) => Path.Combine(sessions.DirectoryFor(s), "compose.yml");
    IReadOnlyList<string> ComposeArgs(DemoSession s, params string[] command) => new[] { "--context", Context, "compose", "--project-name", s.Project, "--file", ComposePath(s) }.Concat(command).ToArray();
    async Task<CommandResult> Run(IReadOnlyList<string> args, int seconds = 120) => await runner.Run(docker, args, TimeSpan.FromSeconds(seconds));
    public async Task CheckEngine()
    {
        var info = await Run(["--context", Context, "info", "--format", "{{.OSType}}"], 20);
        if (info.Code != 0 || info.Output.Trim() != "linux") throw new InvalidOperationException("Docker Desktop aún no está listo en modo contenedores Linux. Ábrelo y espera a que indique que está en funcionamiento; después vuelve a Iniciar. Si falta WSL o pide reiniciar Windows, completa primero esa preparación con TI.");
        var endpoint = await Run(["context", "inspect", Context, "--format", "{{.Endpoints.docker.Host}}"], 20);
        if (endpoint.Code != 0 || !endpoint.Output.Trim().StartsWith("npipe://", StringComparison.OrdinalIgnoreCase)) throw new InvalidOperationException("El motor debe ser Docker Desktop local de Windows. No se utilizará una conexión remota para la demostración.");
        if ((await Run(["--context", Context, "compose", "version", "--short"], 20)).Code != 0) throw new InvalidOperationException("Falta Docker Compose en Docker Desktop. Solicita completar su instalación.");
    }
    public async Task EnsureImages(Action<string> status)
    {
        bool present = true;
        foreach (var i in new[] { manifest.Backend, manifest.Frontend, manifest.Database })
        {
            var inspect = await Run(["--context", Context, "image", "inspect", "--format", "{{.Id}}", i.Tag], 20);
            if (inspect.Code != 0 || inspect.Output.Trim() != i.Id) present = false;
        }
        if (!present)
        {
            status("Preparando los componentes incluidos. La primera vez puede tardar varios minutos; no necesita internet.");
            var archive = Path.Combine(payload, "images.tar.gz");
            await using (var stream = File.OpenRead(archive))
            {
                var actual = Convert.ToHexString(await SHA256.HashDataAsync(stream)).ToLowerInvariant();
                if (actual != manifest.ArchiveSha256) throw new InvalidDataException("El paquete de componentes está incompleto o dañado. Vuelve a instalar desde el archivo original; no se modificaron tus datos.");
            }
            if ((await Run(["--context", Context, "image", "load", "--input", archive], 900)).Code != 0) throw new InvalidOperationException("No fue posible cargar los componentes locales. Comprueba el espacio libre y guarda el Diagnóstico.");
            foreach (var i in new[] { manifest.Backend, manifest.Frontend, manifest.Database })
            {
                var inspect = await Run(["--context", Context, "image", "inspect", "--format", "{{.Id}}", i.Tag], 20);
                if (inspect.Code != 0 || inspect.Output.Trim() != i.Id) throw new InvalidDataException("Los componentes cargados no coinciden con esta versión del paquete.");
            }
        }
    }
    public async Task Start(DemoSession s, Action<string> status, int readyAttempts = 120)
    {
        s.Validate();
        if (s.PackageVersion != manifest.Version) throw new InvalidOperationException("Esta sesión pertenece a otra versión. Conserva su instalador para retomarla; crea una sesión nueva para esta versión. No se migrarán ni borrarán sus datos automáticamente.");
        await CheckEngine(); await EnsureImages(status);
        SessionStore.AtomicWrite(ComposePath(s), ComposeDocument.Render(s, manifest));
        status("Iniciando base de datos y aplicación… Espera a que aparezca Lista para demostrar.");
        if ((await Run(ComposeArgs(s, "up", "--detach", "--no-build", "--pull", "never"), 180)).Code != 0)
            throw new InvalidOperationException("No se pudo iniciar la sesión. Puede haber un puerto ocupado o poco espacio. No se abrirá otra aplicación por error. Usa Diagnóstico o crea una sesión nueva conservando esta.");
        for (int i = 0; i < readyAttempts; i++)
        {
            if (await probe.Ready(s.Url)) { status("Lista para demostrar. Se abrirá PROCO en el navegador. Usuario inicial: almacen. Contraseña: Simulacion123!"); return; }
            await Task.Delay(1000);
        }
        throw new TimeoutException("La aplicación no terminó de iniciar. Los datos se conservan. Pulsa Detener y vuelve a Iniciar, o guarda el Diagnóstico para soporte.");
    }
    public async Task Stop(DemoSession s)
    {
        s.Validate(); if (!File.Exists(ComposePath(s))) return;
        await CheckEngine();
        if ((await Run(ComposeArgs(s, "stop"), 120)).Code != 0) throw new InvalidOperationException("No se pudo confirmar la parada. Los datos no se borraron; revisa Docker Desktop o guarda un Diagnóstico.");
    }
    public async Task<string> Diagnostic(DemoSession? s)
    {
        var report = new StringBuilder($"PROCO Demostración {manifest.Version}\nFuente {manifest.SourceCommit}\nFecha UTC {DateTimeOffset.UtcNow:O}\n");
        foreach (var args in new[] { new[] { "--context", Context, "version", "--format", "{{.Client.Version}} / {{.Server.Version}}" }, new[] { "--context", Context, "info", "--format", "{{.OSType}} {{.Architecture}}" } })
        { var result = await Run(args, 20); report.AppendLine(result.Output).AppendLine(result.Error); }
        if (s != null && File.Exists(ComposePath(s)))
        {
            report.AppendLine($"Sesión {s.Id} / puerto {s.Port}");
            foreach (var args in new[] { ComposeArgs(s, "ps", "--all"), ComposeArgs(s, "logs", "--no-color", "--tail", "80") })
            { var result = await Run(args, 30); report.AppendLine(result.Output).AppendLine(result.Error); }
            report.Replace(s.Password, "[CLAVE OMITIDA]");
        }
        return report.ToString();
    }
}
