using System.Diagnostics;
using System.Runtime.InteropServices;
using Proco.Demo;

namespace Proco.Demo.Launcher;

internal static class Program
{
    [STAThread]
    static int Main(string[] args)
    {
        ApplicationConfiguration.Initialize();
        using var singleton = new Mutex(true, "Local\\PROCO-Demostracion-Launcher", out bool first);
        if (!first) { MessageBox.Show("PROCO Demostración ya está abierto. Busca su ventana en la barra de tareas.", "PROCO"); return 1; }
        try
        {
            if (RuntimeInformation.OSArchitecture != Architecture.X64) throw new NotSupportedException("Este instalador requiere Windows11 con procesador Intel o AMD de64bits. Para Windows ARM se necesita otro paquete.");
            var root = AppContext.BaseDirectory;
            var manifest = DemoManifest.Load(Path.Combine(root, "payload", "manifest.json"));
            var test = args.Contains("--self-test");
            var data = test ? Path.Combine(Path.GetTempPath(), "proco-launcher-test-" + Guid.NewGuid().ToString("N")) : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PROCO", "Demostracion");
            using var form = new MainForm(root, data, manifest);
            if (test)
            {
                form.Show(); Application.DoEvents();
                if (!form.Text.Contains("PROCO") || !form.Controls.Find("start", true).Any() || !form.Controls.Find("manual", true).Any()) return 2;
                using var screenshot = new Bitmap(form.Width, form.Height); form.DrawToBitmap(screenshot, new Rectangle(0, 0, form.Width, form.Height));
                screenshot.Save(Path.Combine(root, "launcher-windows.png")); form.Close();
                Directory.Delete(data, true); return 0;
            }
            Application.Run(form); return 0;
        }
        catch (Exception e) { if (args.Contains("--self-test")) { File.WriteAllText(Path.Combine(AppContext.BaseDirectory, "launcher-test-error.txt"), e.ToString()); return 1; } MessageBox.Show(e.Message, "PROCO — preparación necesaria", MessageBoxButtons.OK, MessageBoxIcon.Information); return 1; }
    }
}

public sealed class MainForm : Form
{
    readonly string root; readonly DemoManifest manifest; readonly SessionStore store;
    readonly ComboBox sessions = new() { DropDownStyle = ComboBoxStyle.DropDownList, Dock = DockStyle.Fill, DisplayMember = "Name" };
    readonly TextBox status = new() { Multiline = true, ReadOnly = true, Dock = DockStyle.Fill, ScrollBars = ScrollBars.Vertical, BackColor = Color.FromArgb(241, 246, 241) };
    readonly FlowLayoutPanel actions = new() { Dock = DockStyle.Fill, WrapContents = true, AutoSize = true };
    bool busy;
    DemoSession? Selected => sessions.SelectedItem as DemoSession;
    public MainForm(string root, string data, DemoManifest manifest)
    {
        this.root = root; this.manifest = manifest; store = new SessionStore(data);
        Text = "PROCO · Demostración local"; Font = new Font("Segoe UI", 11); ClientSize = new Size(810, 690); MinimumSize = new Size(760, 720); StartPosition = FormStartPosition.CenterScreen; BackColor = Color.White;
        var layout = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(26), ColumnCount = 1, RowCount = 9 };
        foreach (var height in new[] { 52, 46, 28, 40, 115, 100, 55, 55 }) layout.RowStyles.Add(new RowStyle(SizeType.Absolute, height)); layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.Controls.Add(new Label { Text = "PROCO | Demostración", Font = new Font("Segoe UI", 23, FontStyle.Bold), ForeColor = Color.FromArgb(24, 89, 70), Dock = DockStyle.Fill });
        layout.Controls.Add(new Label { Text = "Solo simulación · Aplicación y manual disponibles sin internet tras preparar Docker.", Dock = DockStyle.Fill });
        layout.Controls.Add(new Label { Text = "Sesión de trabajo (sus datos se conservan al detener):", Dock = DockStyle.Fill });
        layout.Controls.Add(sessions); layout.Controls.Add(actions); layout.Controls.Add(status);
        var links = new FlowLayoutPanel { Dock = DockStyle.Fill };
        Add("Iniciar demostración", "start", StartDemo); Add("Detener", "stop", StopDemo); Add("Manual", "manual", Manual); Add("Nueva demostración", "new", NewSession); Add("Diagnóstico", "diagnostic", Diagnostic);
        var docker = new LinkLabel { Text = "Preparar Docker Desktop (primera instalación)", AutoSize = true, Margin = new Padding(0, 10, 0, 0) }; docker.LinkClicked += (_, _) => Open("https://docs.docker.com/desktop/setup/install/windows-install/"); links.Controls.Add(docker); layout.Controls.Add(links);
        layout.Controls.Add(new Label { Text = "Usuarios de prueba: almacen, calidad, tecnico, aprobador y otras áreas.\nContraseña de simulación: Simulacion123!", Dock = DockStyle.Fill });
        layout.Controls.Add(new Label { Text = "Cerrar esta ventana no detiene la aplicación. Pulsa Detener al terminar.\nNo se realizan movimientos físicos ni operaciones ERP. Versión: " + manifest.Version, Dock = DockStyle.Fill, ForeColor = Color.DimGray });
        Controls.Add(layout);
        sessions.SelectedIndexChanged += (_, _) => { if (Selected != null) { store.Select(Selected); status.Text = "Sesión seleccionada: " + Selected.Name + "\r\nDirección local: " + Selected.Url + "\r\nPulsa Iniciar para abrirla. Las demás sesiones conservan sus datos."; } };
        RefreshSessions();
        status.Text = "Bienvenido. Pulsa Iniciar demostración. Si Docker Desktop no está instalado o pide preparar WSL, usa el enlace de preparación y solicita apoyo de TI para esa única instalación.";
        FormClosing += (_, e) => { if (busy) { e.Cancel = true; MessageBox.Show(this, "Espera a que termine la operación actual antes de cerrar.", "PROCO"); } };
    }
    void Add(string text, string name, Func<Task> action)
    {
        var b = new Button { Text = text, Name = name, AutoSize = true, Padding = new Padding(9, 5, 9, 5), Margin = new Padding(0, 5, 8, 5) };
        b.Click += async (_, _) => await Work(action); actions.Controls.Add(b);
    }
    async Task Work(Func<Task> action)
    {
        busy = true; actions.Enabled = false; sessions.Enabled = false;
        try { await action(); }
        catch (Exception e) { status.Text = e.Message; MessageBox.Show(this, e.Message, "PROCO — atención", MessageBoxButtons.OK, MessageBoxIcon.Information); }
        finally { busy = false; actions.Enabled = true; sessions.Enabled = true; }
    }
    void RefreshSessions()
    {
        var list = store.List(); if (list.Count == 0) list = new[] { store.Create(manifest.Version) };
        var selected = store.Selected(list); sessions.DataSource = list.ToList(); sessions.SelectedItem = selected;
    }
    string DockerExecutable()
    {
        foreach (var dir in new[] { Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs") })
        { var file = Path.Combine(dir, "Docker", "Docker", "resources", "bin", "docker.exe"); if (File.Exists(file)) return file;
          file = Path.Combine(dir, "DockerDesktop", "resources", "bin", "docker.exe"); if (File.Exists(file)) return file; }
        throw new FileNotFoundException("Primero instala Docker Desktop para Windows con contenedores Linux. Abre el enlace de preparación, completa los pasos que solicite y vuelve a Iniciar. No necesitas instalar Java ni PostgreSQL por separado.");
    }
    DockerRuntime Runtime() => new(DockerExecutable(), new ProcessRunner(), new ReadinessProbe(), store, manifest, Path.Combine(root, "payload"));
    void Status(string text) { status.Text = text; }
    async Task StartDemo()
    {
        if (Selected == null) return;
        var runtime = Runtime();
        try { await runtime.CheckEngine(); }
        catch
        {
            var candidates = new[] { Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Docker", "Docker", "Docker Desktop.exe"), Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "DockerDesktop", "Docker Desktop.exe") };
            var desktop = candidates.FirstOrDefault(File.Exists); if (desktop == null) throw;
            Status("Abriendo Docker Desktop… Si solicita configuración, licencia o reiniciar Windows, complétalo primero con TI."); Open(desktop);
            bool ready = false;
            var deadline = DateTime.UtcNow.AddSeconds(90);
            while (DateTime.UtcNow < deadline) { await Task.Delay(2000); try { await runtime.CheckEngine(); ready = true; break; } catch { } }
            if (!ready) throw new InvalidOperationException("Docker Desktop no terminó de preparar el motor. Completa sus indicaciones y vuelve a Iniciar; si pide activar WSL o virtualización, solicita apoyo de TI.");
        }
        await runtime.Start(Selected, Status); Open(Selected.Url.ToString());
    }
    async Task StopDemo() { if (Selected == null) return; await Runtime().Stop(Selected); Status("Sesión detenida. Sus datos se conservaron. Puedes continuar con Iniciar."); }
    Task Manual() { Open(Path.Combine(root, "manual", "index.html")); return Task.CompletedTask; }
    async Task NewSession()
    {
        if (MessageBox.Show(this, "Se detendrá la sesión seleccionada y se creará otra con los cinco lotes sintéticos iniciales. Los datos anteriores se conservan en la lista. ¿Continuar?", "Nueva demostración", MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes) return;
        if (Selected != null && File.Exists(Path.Combine(store.DirectoryFor(Selected), "compose.yml"))) await Runtime().Stop(Selected);
        var created = store.Create(manifest.Version); store.Select(created); RefreshSessions(); Status("Nueva sesión creada. Pulsa Iniciar; los casos avanzados se realizan siguiendo el manual, no están preaprobados.");
    }
    async Task Diagnostic()
    {
        string report;
        try { report = await Runtime().Diagnostic(Selected); } catch (Exception e) { report = "PROCO " + manifest.Version + "\n" + e.Message; }
        using var save = new SaveFileDialog { FileName = "PROCO-Diagnostico.txt", Filter = "Archivo de texto|*.txt" };
        if (save.ShowDialog(this) == DialogResult.OK) { await File.WriteAllTextAsync(save.FileName, report); Status("Diagnóstico guardado. Compártelo solo con el equipo de soporte; contiene información sintética de la sesión."); }
    }
    static void Open(string path) => Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
}
