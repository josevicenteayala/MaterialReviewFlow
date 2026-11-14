#ifndef PackageVersion
#define PackageVersion "1.0.0"
#endif
#ifndef StageDir
#define StageDir "stage"
#endif
[Setup]
AppId={{BFFB15E8-B116-47AE-AEED-14372CBD0FD5}
AppName=PROCO Demostración
AppVersion={#PackageVersion}
AppPublisher=MaterialReviewFlow
DefaultDirName={localappdata}\Programs\PROCO-Demostracion
DefaultGroupName=PROCO Demostración
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.22000
OutputDir=output
OutputBaseFilename=PROCO-Demostracion-Windows11-x64-{#PackageVersion}
Compression=lzma2/fast
SolidCompression=yes
WizardStyle=modern
DisableWelcomePage=no
UninstallDisplayIcon={app}\PROCO-Demostracion.exe
CloseApplications=yes
SetupLogging=yes
[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"
[Tasks]
Name: "desktopicon"; Description: "Crear acceso directo en el escritorio"; Flags: checkedonce
[Files]
Source: "{#StageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{group}\PROCO Demostración"; Filename: "{app}\PROCO-Demostracion.exe"
Name: "{group}\Manual de usuario"; Filename: "{app}\manual\index.html"
Name: "{userdesktop}\PROCO Demostración"; Filename: "{app}\PROCO-Demostracion.exe"; Tasks: desktopicon
[Run]
Filename: "{app}\PROCO-Demostracion.exe"; Description: "Abrir PROCO Demostración"; Flags: nowait postinstall skipifsilent
[Code]
function InitializeSetup(): Boolean;
begin
  Result := not IsArm64;
  if not Result then MsgBox('Este paquete requiere Windows 11 con procesador Intel o AMD de 64 bits. Para Windows ARM se necesita otro instalador.', mbInformation, MB_OK);
end;
procedure InitializeWizard();
begin
  WizardForm.WelcomeLabel2.Caption := 'Este asistente instala PROCO y su manual para demostraciones locales.' + #13#10#13#10 +
    'La primera vez se necesita Docker Desktop con contenedores Linux y WSL 2 preparado. El panel incluye un enlace a las instrucciones oficiales. Esa preparación puede requerir apoyo de TI y reiniciar Windows.' + #13#10#13#10 +
    'Después de cargar los componentes incluidos, la demostración funciona sin internet. Los datos se conservan al detener y al desinstalar el panel. No se elimina Docker ni sus volúmenes automáticamente.';
end;
