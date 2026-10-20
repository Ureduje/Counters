using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("Namestitev — Nadzorna plošča vzpenjače")]
[assembly: AssemblyDescription("Uporabniški namestitveni program (brez skrbniških pravic)")]
[assembly: AssemblyCompany("Vzpenjača Kontrola")]
[assembly: AssemblyProduct("Vzpenjača Kontrola")]
[assembly: AssemblyVersion("1.2.3.0")]
[assembly: AssemblyFileVersion("1.2.3.0")]

namespace VzpenjacaInstaller
{
    public class InstallerForm : Form
    {
        private const string AppExeName = "VzpenjacaKontrola.exe";
        private const string UninstallExeName = "Odstrani.exe";
        private const string ShortcutName = "Nadzorna plošča vzpenjače.lnk";
        private const string RegistryKeyPath = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\VzpenjacaKontrola";

        private readonly TextBox _txtInstallDir;
        private readonly CheckBox _chkDesktopShortcut;
        private readonly CheckBox _chkStartMenuShortcut;
        private readonly CheckBox _chkLaunchAfter;
        private readonly Button _btnInstall;
        private readonly Label _lblStatus;

        public InstallerForm()
        {
            Text = "Namestitev — Nadzorna plošča vzpenjače (brez skrbniških pravic)";
            Size = new Size(560, 390);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(15, 23, 42);
            ForeColor = Color.White;
            Font = new Font("Segoe UI", 10f, FontStyle.Regular);

            try
            {
                Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            }
            catch { }

            string defaultDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                @"Programs\VzpenjacaKontrola");

            Label lblHeader = new Label
            {
                Text = "🚡  Namestitev programa Nadzorna plošča vzpenjače",
                Font = new Font("Segoe UI", 13.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(248, 250, 252),
                Location = new Point(22, 18),
                AutoSize = true
            };

            Label lblSub = new Label
            {
                Text = "Namestitev v uporabniški profil — skrbniške (Administrator) pravice NISO potrebne.\n" +
                       "Vključuje nadzor za postaji 10.12.220.34 (Zgoraj) in 10.12.220.36 (Spodaj).",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Regular),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(24, 50),
                Size = new Size(500, 42)
            };

            Label lblFolder = new Label
            {
                Text = "Mapa namestitve (uporabniška mapa):",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(203, 213, 225),
                Location = new Point(24, 102),
                AutoSize = true
            };

            _txtInstallDir = new TextBox
            {
                Text = defaultDir,
                Location = new Point(24, 126),
                Width = 400,
                BackColor = Color.FromArgb(30, 41, 59),
                ForeColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Consolas", 10f, FontStyle.Regular)
            };

            Button btnBrowse = new Button
            {
                Text = "Prebrskaj...",
                Location = new Point(432, 124),
                Size = new Size(92, 27),
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(51, 65, 85),
                ForeColor = Color.White,
                Cursor = Cursors.Hand
            };
            btnBrowse.FlatAppearance.BorderColor = Color.FromArgb(100, 116, 139);
            btnBrowse.Click += (s, e) =>
            {
                using (FolderBrowserDialog fbd = new FolderBrowserDialog())
                {
                    fbd.Description = "Izberite mapo za namestitev (uporabniška mapa brez skrbniških pravic):";
                    fbd.SelectedPath = _txtInstallDir.Text;
                    if (fbd.ShowDialog(this) == DialogResult.OK)
                    {
                        _txtInstallDir.Text = Path.Combine(fbd.SelectedPath, "VzpenjacaKontrola");
                    }
                }
            };

            _chkDesktopShortcut = new CheckBox
            {
                Text = "Ustvari bližnjico na namizju (Desktop)",
                Checked = true,
                Location = new Point(26, 170),
                AutoSize = true,
                ForeColor = Color.FromArgb(226, 232, 240)
            };

            _chkStartMenuShortcut = new CheckBox
            {
                Text = "Ustvari bližnjico v meniju Start (Start Menu)",
                Checked = true,
                Location = new Point(26, 200),
                AutoSize = true,
                ForeColor = Color.FromArgb(226, 232, 240)
            };

            _chkLaunchAfter = new CheckBox
            {
                Text = "Zaženi aplikacijo po končani namestitvi",
                Checked = true,
                Location = new Point(26, 230),
                AutoSize = true,
                ForeColor = Color.FromArgb(226, 232, 240)
            };

            _lblStatus = new Label
            {
                Text = "Pripravljeno za namestitev.",
                Font = new Font("Segoe UI", 9.0f, FontStyle.Regular),
                ForeColor = Color.FromArgb(125, 211, 252),
                Location = new Point(24, 268),
                Size = new Size(500, 22)
            };

            _btnInstall = new Button
            {
                Text = "Namesti zdaj",
                Font = new Font("Segoe UI", 11f, FontStyle.Bold),
                Location = new Point(364, 298),
                Size = new Size(160, 40),
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(22, 163, 74),
                ForeColor = Color.White,
                Cursor = Cursors.Hand
            };
            _btnInstall.FlatAppearance.BorderColor = Color.FromArgb(74, 222, 128);
            _btnInstall.Click += (s, e) => RunInteractiveInstall();

            Button btnCancel = new Button
            {
                Text = "Zapri",
                Font = new Font("Segoe UI", 10f, FontStyle.Regular),
                Location = new Point(254, 298),
                Size = new Size(98, 40),
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(51, 65, 85),
                ForeColor = Color.White,
                Cursor = Cursors.Hand
            };
            btnCancel.FlatAppearance.BorderColor = Color.FromArgb(100, 116, 139);
            btnCancel.Click += (s, e) => Close();

            Controls.Add(lblHeader);
            Controls.Add(lblSub);
            Controls.Add(lblFolder);
            Controls.Add(_txtInstallDir);
            Controls.Add(btnBrowse);
            Controls.Add(_chkDesktopShortcut);
            Controls.Add(_chkStartMenuShortcut);
            Controls.Add(_chkLaunchAfter);
            Controls.Add(_lblStatus);
            Controls.Add(btnCancel);
            Controls.Add(_btnInstall);
        }

        private void RunInteractiveInstall()
        {
            try
            {
                _btnInstall.Enabled = false;
                _lblStatus.Text = "Nameščanje datotek...";
                Application.DoEvents();

                string targetDir = _txtInstallDir.Text.Trim();
                string installedExe = PerformInstall(
                    targetDir,
                    _chkDesktopShortcut.Checked,
                    _chkStartMenuShortcut.Checked);

                _lblStatus.ForeColor = Color.FromArgb(74, 222, 128);
                _lblStatus.Text = "Namestitev je uspešno zaključena!";

                if (_chkLaunchAfter.Checked && File.Exists(installedExe))
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = installedExe,
                        WorkingDirectory = targetDir,
                        UseShellExecute = true
                    });
                }

                MessageBox.Show(
                    "Program Nadzorna plošča vzpenjače je bil uspešno nameščen v:\n" + targetDir,
                    "Namestitev uspešna",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Information);

                Close();
            }
            catch (Exception ex)
            {
                _btnInstall.Enabled = true;
                _lblStatus.ForeColor = Color.FromArgb(248, 113, 113);
                _lblStatus.Text = "Napaka: " + ex.Message;
                MessageBox.Show(
                    "Med namestitvijo je prišlo do napake:\n" + ex.Message,
                    "Napaka pri namestitvi",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            }
        }

        public static string PerformInstall(string targetDir, bool createDesktop, bool createStartMenu)
        {
            if (string.IsNullOrWhiteSpace(targetDir))
            {
                targetDir = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    @"Programs\VzpenjacaKontrola");
            }

            if (!Directory.Exists(targetDir))
            {
                Directory.CreateDirectory(targetDir);
            }

            string targetExePath = Path.Combine(targetDir, AppExeName);

            // Close any running instance in target directory if updating
            foreach (Process proc in Process.GetProcessesByName("VzpenjacaKontrola"))
            {
                try
                {
                    proc.CloseMainWindow();
                    if (!proc.WaitForExit(1000))
                    {
                        proc.Kill();
                        proc.WaitForExit(2000);
                    }
                }
                catch { }
            }

            // Extract embedded VzpenjacaKontrola.exe resource
            Assembly asm = Assembly.GetExecutingAssembly();
            using (Stream resStream = asm.GetManifestResourceStream("VzpenjacaKontrola.App.exe"))
            {
                if (resStream == null)
                {
                    throw new InvalidOperationException("Vgrajena datoteka aplikacije ni bila najdena v namestitvenem paketu.");
                }
                using (FileStream fs = new FileStream(targetExePath, FileMode.Create, FileAccess.Write))
                {
                    resStream.CopyTo(fs);
                }
            }

            // Copy installer as Odstrani.exe for per-user uninstallation
            string uninstallExePath = Path.Combine(targetDir, UninstallExeName);
            try
            {
                string currentExe = Application.ExecutablePath;
                if (!string.Equals(Path.GetFullPath(currentExe), Path.GetFullPath(uninstallExePath), StringComparison.OrdinalIgnoreCase))
                {
                    File.Copy(currentExe, uninstallExePath, true);
                }
            }
            catch { }

            // Create Desktop shortcut
            if (createDesktop)
            {
                string desktopDir = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                CreateShortcut(Path.Combine(desktopDir, ShortcutName), targetExePath, targetDir, "Nadzorna plošča vzpenjače (10.12.220.34 & 10.12.220.36)");
            }

            // Create Start Menu shortcut
            if (createStartMenu)
            {
                string programsDir = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
                CreateShortcut(Path.Combine(programsDir, ShortcutName), targetExePath, targetDir, "Nadzorna plošča vzpenjače (10.12.220.34 & 10.12.220.36)");
            }

            // Register in HKCU Uninstall and HKCU Run (no admin needed!)
            try
            {
                using (RegistryKey key = Registry.CurrentUser.CreateSubKey(RegistryKeyPath))
                {
                    if (key != null)
                    {
                        key.SetValue("DisplayName", "Nadzorna plošča vzpenjače");
                        key.SetValue("DisplayVersion", "1.2.3");
                        key.SetValue("Publisher", "Vzpenjača Kontrola");
                        key.SetValue("DisplayIcon", targetExePath);
                        key.SetValue("InstallLocation", targetDir);
                        key.SetValue("UninstallString", "\"" + uninstallExePath + "\" /uninstall");
                        key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                        key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                    }
                }

                using (RegistryKey runKey = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run", true))
                {
                    if (runKey != null)
                    {
                        runKey.SetValue("VzpenjacaKontrola", "\"" + targetExePath + "\"");
                    }
                }
            }
            catch { }

            return targetExePath;
        }

        public static void PerformUninstall(bool silent)
        {
            string installDir = null;
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(RegistryKeyPath, false))
                {
                    if (key != null)
                    {
                        installDir = key.GetValue("InstallLocation") as string;
                    }
                }
            }
            catch { }

            if (string.IsNullOrEmpty(installDir))
            {
                installDir = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    @"Programs\VzpenjacaKontrola");
            }

            if (!silent)
            {
                DialogResult dr = MessageBox.Show(
                    "Ali želite odstraniti program Nadzorna plošča vzpenjače iz vašega uporabniškega profila?",
                    "Odstranitev programa",
                    MessageBoxButtons.YesNo,
                    MessageBoxIcon.Question);
                if (dr != DialogResult.Yes) return;
            }

            foreach (Process proc in Process.GetProcessesByName("VzpenjacaKontrola"))
            {
                try
                {
                    proc.CloseMainWindow();
                    if (!proc.WaitForExit(1000))
                    {
                        proc.Kill();
                        proc.WaitForExit(2000);
                    }
                }
                catch { }
            }

            // Remove shortcuts
            try
            {
                string desktopShortcut = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), ShortcutName);
                if (File.Exists(desktopShortcut)) File.Delete(desktopShortcut);

                string startMenuShortcut = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), ShortcutName);
                if (File.Exists(startMenuShortcut)) File.Delete(startMenuShortcut);
            }
            catch { }

            // Remove HKCU registry entries
            try
            {
                Registry.CurrentUser.DeleteSubKeyTree(RegistryKeyPath, false);
                using (RegistryKey runKey = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run", true))
                {
                    if (runKey != null)
                    {
                        runKey.DeleteValue("VzpenjacaKontrola", false);
                    }
                }
            }
            catch { }

            // Remove application executable
            try
            {
                string appExe = Path.Combine(installDir, AppExeName);
                if (File.Exists(appExe)) File.Delete(appExe);
            }
            catch { }

            if (!silent)
            {
                MessageBox.Show(
                    "Program Nadzorna plošča vzpenjače je bil uspešno odstranjen.",
                    "Odstranitev zaključena",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Information);
            }
        }

        private static void CreateShortcut(string shortcutPath, string targetExe, string workingDir, string description)
        {
            try
            {
                Type shellType = Type.GetTypeFromProgID("WScript.Shell");
                if (shellType == null) return;
                object shell = Activator.CreateInstance(shellType);
                object shortcut = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { shortcutPath });
                Type shortcutType = shortcut.GetType();
                shortcutType.InvokeMember("TargetPath", BindingFlags.SetProperty, null, shortcut, new object[] { targetExe });
                shortcutType.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, shortcut, new object[] { workingDir });
                shortcutType.InvokeMember("Description", BindingFlags.SetProperty, null, shortcut, new object[] { description });
                shortcutType.InvokeMember("IconLocation", BindingFlags.SetProperty, null, shortcut, new object[] { targetExe + ",0" });
                shortcutType.InvokeMember("Save", BindingFlags.InvokeMethod, null, shortcut, null);
            }
            catch { }
        }

        [STAThread]
        static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            bool isUninstall = false;
            bool isSilent = false;

            foreach (string arg in args)
            {
                if (arg.Equals("/uninstall", StringComparison.OrdinalIgnoreCase) ||
                    arg.Equals("-uninstall", StringComparison.OrdinalIgnoreCase))
                {
                    isUninstall = true;
                }
                else if (arg.Equals("/S", StringComparison.OrdinalIgnoreCase) ||
                         arg.Equals("/silent", StringComparison.OrdinalIgnoreCase) ||
                         arg.Equals("/quiet", StringComparison.OrdinalIgnoreCase))
                {
                    isSilent = true;
                }
            }

            if (isUninstall)
            {
                PerformUninstall(isSilent);
                return;
            }

            if (isSilent)
            {
                PerformInstall(null, true, true);
                return;
            }

            Application.Run(new InstallerForm());
        }
    }
}
