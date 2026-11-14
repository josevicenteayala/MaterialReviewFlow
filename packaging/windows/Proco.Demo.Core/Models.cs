using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace Proco.Demo;

public sealed record ImageSpec(string Tag, string Id);
public sealed record DemoManifest(string Version, string SourceCommit, string Platform, string ArchiveSha256, ImageSpec Backend, ImageSpec Frontend, ImageSpec Database)
{
    public static DemoManifest Load(string file)
    {
        var m = JsonSerializer.Deserialize<DemoManifest>(File.ReadAllText(file)) ?? throw new InvalidDataException("Paquete incompleto: falta su versión.");
        if (!Regex.IsMatch(m.Version ?? "", @"^[a-zA-Z0-9._-]{1,80}$") || !Regex.IsMatch(m.SourceCommit ?? "", "^[a-f0-9]{40}$") || m.Platform != "linux/amd64" || !Regex.IsMatch(m.ArchiveSha256 ?? "", "^[a-f0-9]{64}$"))
            throw new InvalidDataException("Metadatos del paquete no válidos.");
        foreach (var i in new[] { m.Backend, m.Frontend, m.Database })
            if (i == null || !Regex.IsMatch(i.Tag ?? "", @"^[a-z0-9][a-z0-9._/-]*:[a-zA-Z0-9._-]+$") || !Regex.IsMatch(i.Id ?? "", "^sha256:[a-f0-9]{64}$"))
                throw new InvalidDataException("Imagen del paquete no válida.");
        return m;
    }
}

public sealed record DemoSession(string Id, string Name, int Port, string Password, DateTimeOffset CreatedAt, string PackageVersion)
{
    public string Project => "proco-demo-" + Id;
    public Uri Url => new($"http://127.0.0.1:{Port}/");
    public void Validate()
    {
        if (!Regex.IsMatch(Id ?? "", "^[a-f0-9]{16}$") || string.IsNullOrWhiteSpace(Name) || Name.Length > 100 || Port is < 1024 or > 65535 || !Regex.IsMatch(Password ?? "", "^[a-f0-9]{48}$") || !Regex.IsMatch(PackageVersion ?? "", @"^[a-zA-Z0-9._-]{1,80}$"))
            throw new InvalidDataException("La sesión guardada no es válida; no se iniciará otra sobre sus datos. Contacta a soporte.");
    }
}

public sealed class SessionStore(string root)
{
    public string Root { get; } = Path.GetFullPath(root);
    public string DirectoryFor(DemoSession s) { s.Validate(); return Path.Combine(Root, "sessions", s.Id); }
    public IReadOnlyList<DemoSession> List()
    {
        var path = Path.Combine(Root, "sessions");
        if (!Directory.Exists(path)) return [];
        return Directory.EnumerateFiles(path, "session.json", SearchOption.AllDirectories).Select(file =>
        {
            var s = JsonSerializer.Deserialize<DemoSession>(File.ReadAllText(file)) ?? throw new InvalidDataException("No se puede leer una sesión guardada.");
            s.Validate();
            if (Path.GetFileName(Path.GetDirectoryName(file)) != s.Id) throw new InvalidDataException("La carpeta de sesión no coincide con sus datos.");
            return s;
        }).OrderByDescending(s => s.CreatedAt).ToArray();
    }
    public DemoSession Create(string version)
    {
        var s = new DemoSession(Convert.ToHexString(RandomNumberGenerator.GetBytes(8)).ToLowerInvariant(), "Demostración " + DateTime.Now.ToString("dd/MM/yyyy HH:mm"), FreePort(), Convert.ToHexString(RandomNumberGenerator.GetBytes(24)).ToLowerInvariant(), DateTimeOffset.UtcNow, version);
        s.Validate(); var dir = DirectoryFor(s); Directory.CreateDirectory(dir);
        AtomicWrite(Path.Combine(dir, "session.json"), JsonSerializer.Serialize(s, new JsonSerializerOptions { WriteIndented = true }));
        return s;
    }
    public DemoSession? Selected(IReadOnlyList<DemoSession> sessions)
    {
        var path = Path.Combine(Root, "selected.txt");
        if (!File.Exists(path)) return sessions.FirstOrDefault();
        var id = File.ReadAllText(path).Trim();
        return sessions.FirstOrDefault(s => s.Id == id) ?? throw new InvalidDataException("No se encuentra la sesión seleccionada. Los datos no se reemplazarán automáticamente.");
    }
    public void Select(DemoSession s) => AtomicWrite(Path.Combine(Root, "selected.txt"), s.Id);
    public static void AtomicWrite(string path, string value)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temp = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try { File.WriteAllText(temp, value); File.Move(temp, path, true); }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    static int FreePort()
    {
        var socket = new TcpListener(IPAddress.Loopback, 0); socket.Start();
        try { return ((IPEndPoint)socket.LocalEndpoint).Port; } finally { socket.Stop(); }
    }
}

public static class ComposeDocument
{
    public static string Render(DemoSession s, DemoManifest m)
    {
        s.Validate();
        return $$"""
services:
  db:
    image: '{{m.Database.Tag}}'
    platform: linux/amd64
    pull_policy: never
    environment:
      POSTGRES_DB: proco
      POSTGRES_USER: proco
      POSTGRES_PASSWORD: '{{s.Password}}'
    volumes:
      - proco-data:/var/lib/postgresql/data
    healthcheck:
      test: ['CMD-SHELL', 'pg_isready -U proco -d proco']
      interval: 3s
      timeout: 3s
      retries: 40
  backend:
    image: '{{m.Backend.Tag}}'
    platform: linux/amd64
    pull_policy: never
    environment:
      DB_URL: jdbc:postgresql://db:5432/proco
      DB_USER: proco
      DB_PASSWORD: '{{s.Password}}'
    depends_on:
      db:
        condition: service_healthy
  frontend:
    image: '{{m.Frontend.Tag}}'
    platform: linux/amd64
    pull_policy: never
    depends_on:
      - backend
    ports:
      - '127.0.0.1:{{s.Port}}:80'
volumes:
  proco-data:
networks:
  default:
    internal: true
""";
    }
}
