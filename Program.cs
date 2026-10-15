using System;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Media;
using System.Net.Sockets;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Shapes;
using System.Windows.Shell;
using System.Windows.Threading;
using Microsoft.Win32;

[assembly: AssemblyTitle("Vzpenjača")]
[assembly: AssemblyDescription("Nadzorna plošča vzpenjače — Zgornja in spodnja postaja")]
[assembly: AssemblyCompany("Vzpenjača")]
[assembly: AssemblyProduct("Vzpenjača")]
[assembly: AssemblyVersion("1.0.2.0")]
[assembly: AssemblyFileVersion("1.0.2.0")]

namespace VzpenjacaKontrola
{
    public class StationTelemetry
    {
        public int CurrentPersons { get; set; }
        public int MaxPersons { get; set; }
        public long RemainingTimeMs { get; set; }
        public long SetTimeMs { get; set; }
        public int StationType { get; set; }
    }

    public class AppConfig
    {
        private const string RunRegistryPath = @"Software\Microsoft\Windows\CurrentVersion\Run";
        private const string RunValueName = "VzpenjacaKontrola";

        public string IpZgoraj = "10.12.220.34";
        public string IpSpodaj = "10.12.220.36";
        public bool AlwaysOnTop = false;
        public bool StartWithWindows = true;
        public double WindowWidth = 680;
        public double WindowHeight = 660;
        public double WindowLeft = double.NaN;
        public double WindowTop = double.NaN;
        public double CounterScale = 1.0;
        public bool HideWindowControls = false;
        public bool SoundEnabled = true;
        public bool VoiceAnnouncementEnabled = true;
        public bool MuteZgoraj = false;
        public bool MuteSpodaj = false;
        public bool DimZgoraj = false;
        public bool DimSpodaj = false;
        public bool ShowIntervalBgZgoraj = true;
        public bool ShowIntervalBgSpodaj = true;

        private static string ConfigPath
        {
            get
            {
                string dir = System.IO.Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "VzpenjacaKontrola");
                return System.IO.Path.Combine(dir, "settings.ini");
            }
        }

        public static AppConfig Load()
        {
            AppConfig cfg = new AppConfig();
            try
            {
                if (File.Exists(ConfigPath))
                {
                    foreach (string line in File.ReadAllLines(ConfigPath, Encoding.UTF8))
                    {
                        string trimmed = line.Trim();
                        if (string.IsNullOrEmpty(trimmed) || trimmed.StartsWith("#")) continue;
                        int idx = trimmed.IndexOf('=');
                        if (idx <= 0) continue;
                        string key = trimmed.Substring(0, idx).Trim();
                        string val = trimmed.Substring(idx + 1).Trim();
                        if (key.Equals("IpZgoraj", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrEmpty(val))
                            cfg.IpZgoraj = val;
                        else if (key.Equals("IpSpodaj", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrEmpty(val))
                            cfg.IpSpodaj = val;
                        else if (key.Equals("AlwaysOnTop", StringComparison.OrdinalIgnoreCase))
                            cfg.AlwaysOnTop = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        else if (key.Equals("StartWithWindows", StringComparison.OrdinalIgnoreCase))
                            cfg.StartWithWindows = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        else if (key.Equals("WindowWidth", StringComparison.OrdinalIgnoreCase))
                        {
                            double w;
                            if (double.TryParse(val, NumberStyles.Float, CultureInfo.InvariantCulture, out w) && w >= 280)
                                cfg.WindowWidth = w;
                        }
                        else if (key.Equals("WindowHeight", StringComparison.OrdinalIgnoreCase))
                        {
                            double h;
                            if (double.TryParse(val, NumberStyles.Float, CultureInfo.InvariantCulture, out h) && h >= 420)
                                cfg.WindowHeight = h;
                        }
                        else if (key.Equals("WindowLeft", StringComparison.OrdinalIgnoreCase))
                        {
                            double l;
                            if (double.TryParse(val, NumberStyles.Float, CultureInfo.InvariantCulture, out l))
                                cfg.WindowLeft = l;
                        }
                        else if (key.Equals("WindowTop", StringComparison.OrdinalIgnoreCase))
                        {
                            double t;
                            if (double.TryParse(val, NumberStyles.Float, CultureInfo.InvariantCulture, out t))
                                cfg.WindowTop = t;
                        }
                        else if (key.Equals("CounterScale", StringComparison.OrdinalIgnoreCase))
                        {
                            double cs;
                            if (double.TryParse(val, NumberStyles.Float, CultureInfo.InvariantCulture, out cs) && cs >= 0.6 && cs <= 2.2)
                                cfg.CounterScale = cs;
                        }
                        else if (key.Equals("HideWindowControls", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.HideWindowControls = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("SoundEnabled", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.SoundEnabled = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("VoiceAnnouncementEnabled", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.VoiceAnnouncementEnabled = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("MuteZgoraj", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.MuteZgoraj = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("MuteSpodaj", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.MuteSpodaj = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("DimZgoraj", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.DimZgoraj = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("DimSpodaj", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.DimSpodaj = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("ShowIntervalBgZgoraj", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.ShowIntervalBgZgoraj = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("ShowIntervalBgSpodaj", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.ShowIntervalBgSpodaj = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                    }
                }
            }
            catch { }
            return cfg;
        }

        public void Save()
        {
            try
            {
                string dir = System.IO.Path.GetDirectoryName(ConfigPath);
                if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
                StringBuilder sb = new StringBuilder();
                sb.AppendLine("IpZgoraj=" + IpZgoraj);
                sb.AppendLine("IpSpodaj=" + IpSpodaj);
                sb.AppendLine("AlwaysOnTop=" + (AlwaysOnTop ? "true" : "false"));
                sb.AppendLine("StartWithWindows=" + (StartWithWindows ? "true" : "false"));
                sb.AppendLine("WindowWidth=" + WindowWidth.ToString("0", CultureInfo.InvariantCulture));
                sb.AppendLine("WindowHeight=" + WindowHeight.ToString("0", CultureInfo.InvariantCulture));
                if (!double.IsNaN(WindowLeft) && !double.IsInfinity(WindowLeft))
                    sb.AppendLine("WindowLeft=" + WindowLeft.ToString("0", CultureInfo.InvariantCulture));
                if (!double.IsNaN(WindowTop) && !double.IsInfinity(WindowTop))
                    sb.AppendLine("WindowTop=" + WindowTop.ToString("0", CultureInfo.InvariantCulture));
                sb.AppendLine("CounterScale=" + CounterScale.ToString("0.00", CultureInfo.InvariantCulture));
                sb.AppendLine("HideWindowControls=" + (HideWindowControls ? "true" : "false"));
                sb.AppendLine("SoundEnabled=" + (SoundEnabled ? "true" : "false"));
                sb.AppendLine("VoiceAnnouncementEnabled=" + (VoiceAnnouncementEnabled ? "true" : "false"));
                sb.AppendLine("MuteZgoraj=" + (MuteZgoraj ? "true" : "false"));
                sb.AppendLine("MuteSpodaj=" + (MuteSpodaj ? "true" : "false"));
                sb.AppendLine("DimZgoraj=" + (DimZgoraj ? "true" : "false"));
                sb.AppendLine("DimSpodaj=" + (DimSpodaj ? "true" : "false"));
                sb.AppendLine("ShowIntervalBgZgoraj=" + (ShowIntervalBgZgoraj ? "true" : "false"));
                sb.AppendLine("ShowIntervalBgSpodaj=" + (ShowIntervalBgSpodaj ? "true" : "false"));
                File.WriteAllText(ConfigPath, sb.ToString(), Encoding.UTF8);
            }
            catch { }
        }

        public static void ApplyAutostart(bool enable)
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(RunRegistryPath, true))
                {
                    if (key == null) return;
                    if (enable)
                    {
                        string installedExe = System.IO.Path.Combine(
                            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                            @"Programs\VzpenjacaKontrola\VzpenjacaKontrola.exe");
                        string exeToRun = File.Exists(installedExe)
                            ? installedExe
                            : Assembly.GetExecutingAssembly().Location;
                        key.SetValue(RunValueName, "\"" + exeToRun + "\"");
                    }
                    else
                    {
                        key.DeleteValue(RunValueName, false);
                    }
                }
            }
            catch { }
        }
    }

    public class FunicularClient
    {
        private readonly SemaphoreSlim _lock = new SemaphoreSlim(1, 1);
        public string HostIp { get; set; }
        public int Port { get; set; }

        public FunicularClient(string hostIp, int port = 80)
        {
            HostIp = hostIp;
            Port = port;
        }

        public async Task<StationTelemetry> SendOperatorCommandAsync(int commandId, int timeoutMs = 1800)
        {
            await _lock.WaitAsync().ConfigureAwait(false);
            try
            {
                string path = "/Funicular/Operater?" + commandId.ToString(CultureInfo.InvariantCulture);
                string rawResponse = await ExecuteRawGetAsync(HostIp, Port, path, timeoutMs).ConfigureAwait(false);
                return ParseTelemetry(rawResponse);
            }
            finally
            {
                _lock.Release();
            }
        }

        private static async Task<string> ExecuteRawGetAsync(string host, int port, string path, int timeoutMs)
        {
            using (TcpClient client = new TcpClient())
            {
                Task connectTask = client.ConnectAsync(host, port);
                Task completedConnect = await Task.WhenAny(connectTask, Task.Delay(timeoutMs)).ConfigureAwait(false);
                if (completedConnect != connectTask)
                    throw new TimeoutException("Timeout");
                await connectTask.ConfigureAwait(false);

                using (NetworkStream stream = client.GetStream())
                {
                    string requestText =
                        "GET " + path + " HTTP/1.1\r\n" +
                        "Host: " + host + "\r\n" +
                        "Connection: close\r\n\r\n";
                    byte[] reqBytes = Encoding.ASCII.GetBytes(requestText);

                    Task writeTask = stream.WriteAsync(reqBytes, 0, reqBytes.Length);
                    if (await Task.WhenAny(writeTask, Task.Delay(timeoutMs)).ConfigureAwait(false) != writeTask)
                        throw new TimeoutException("Timeout");
                    await writeTask.ConfigureAwait(false);

                    using (MemoryStream ms = new MemoryStream())
                    {
                        byte[] buffer = new byte[512];
                        while (true)
                        {
                            Task<int> readTask = stream.ReadAsync(buffer, 0, buffer.Length);
                            if (await Task.WhenAny(readTask, Task.Delay(timeoutMs)).ConfigureAwait(false) != readTask)
                            {
                                if (ms.Length > 0) break;
                                throw new TimeoutException("Timeout");
                            }
                            int bytesRead = await readTask.ConfigureAwait(false);
                            if (bytesRead <= 0) break;
                            ms.Write(buffer, 0, bytesRead);

                            string partial = Encoding.ASCII.GetString(ms.ToArray());
                            string bodyCandidate = ExtractPayloadLine(partial);
                            if (bodyCandidate != null)
                            {
                                string[] parts = bodyCandidate.Split('#');
                                if (parts.Length >= 5 && parts[4].Trim().Length >= 1)
                                    break;
                            }
                        }
                        return Encoding.ASCII.GetString(ms.ToArray());
                    }
                }
            }
        }

        private static string ExtractPayloadLine(string raw)
        {
            if (string.IsNullOrEmpty(raw)) return null;
            int headerEnd = raw.IndexOf("\r\n\r\n", StringComparison.Ordinal);
            string body = headerEnd >= 0 ? raw.Substring(headerEnd + 4) : raw;
            string[] lines = body.Split(new char[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            foreach (string line in lines)
            {
                string trimmed = line.Trim();
                if (trimmed.Contains("#"))
                    return trimmed;
            }
            return null;
        }

        private static StationTelemetry ParseTelemetry(string rawResponse)
        {
            string payload = ExtractPayloadLine(rawResponse);
            if (string.IsNullOrEmpty(payload))
                throw new InvalidDataException("Invalid response");

            string[] parts = payload.Split('#');
            if (parts.Length < 4)
                throw new InvalidDataException("Incomplete response");

            int curPersons = 0;
            int maxPersons = 0;
            long remTimeMs = 0;
            long setTimeMs = 0;
            int stationType = 0;

            int.TryParse(parts[0].Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out curPersons);
            int.TryParse(parts[1].Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out maxPersons);
            long.TryParse(parts[2].Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out remTimeMs);
            long.TryParse(parts[3].Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out setTimeMs);
            if (parts.Length >= 5)
                int.TryParse(parts[4].Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out stationType);

            return new StationTelemetry
            {
                CurrentPersons = curPersons,
                MaxPersons = maxPersons,
                RemainingTimeMs = Math.Max(0, remTimeMs),
                SetTimeMs = Math.Max(0, setTimeMs),
                StationType = stationType
            };
        }
    }

    internal static class ChimePlayer
    {
        public static bool Enabled = true;
        public static bool VoiceEnabled = true;

        private static readonly byte[] _wavTimeSingle;
        private static readonly byte[] _wavTimeTriple;
        private static readonly byte[] _wavPersonsSingle;
        private static readonly byte[] _wavPersonsTriple;
        private static readonly object _playLock = new object();
        private static DateTime _lastDepartureSpeechUtc = DateTime.MinValue;

        static ChimePlayer()
        {
            // Time chimes: High, bright crystal bell timbre
            _wavTimeSingle = BuildChimeWav(new double[] { 932.33 }, 0.42, 0.0, true);
            _wavTimeTriple = BuildChimeWav(new double[] { 1046.50, 1046.50, 1318.51 }, 0.17, 0.045, true);

            // Persons chimes: Warm lower-mid fifth-chord timbre (distinct from time chimes)
            _wavPersonsSingle = BuildChimeWav(new double[] { 523.25 }, 0.42, 0.0, false);
            _wavPersonsTriple = BuildChimeWav(new double[] { 587.33, 587.33, 783.99 }, 0.18, 0.045, false);
        }

        public static void PlayTimeSingle()
        {
            PlayWavBuffer(_wavTimeSingle);
        }

        public static void PlayTimeTriple()
        {
            PlayWavBuffer(_wavTimeTriple);
        }

        public static void PlayPersonsSingle()
        {
            PlayWavBuffer(_wavPersonsSingle);
        }

        public static void PlayPersonsTriple()
        {
            PlayWavBuffer(_wavPersonsTriple);
        }

        public static void SpeakDeparture(int minutes)
        {
            if (!VoiceEnabled) return;
            DateTime now = DateTime.UtcNow;
            if ((now - _lastDepartureSpeechUtc).TotalSeconds < 4.0) return;
            _lastDepartureSpeechUtc = now;

            if (minutes <= 0) minutes = 1;
            string text = "Vzpenjača je odpeljala. Naslednja vožnja je čez " + FormatSlovenianMinutesPhrase(minutes) + ".";

            Thread t = new Thread(() =>
            {
                try
                {
                    lock (_playLock)
                    {
                        Type voiceType = Type.GetTypeFromProgID("SAPI.SpVoice");
                        if (voiceType == null) return;
                        object voice = Activator.CreateInstance(voiceType);
                        string tokenKey = FindSlovenianVoiceTokenPath();
                        if (!string.IsNullOrEmpty(tokenKey))
                        {
                            try
                            {
                                Type tokenType = Type.GetTypeFromProgID("SAPI.SpObjectToken");
                                if (tokenType != null)
                                {
                                    object token = Activator.CreateInstance(tokenType);
                                    tokenType.InvokeMember("SetId", BindingFlags.InvokeMethod, null, token, new object[] { tokenKey });
                                    voiceType.InvokeMember("Voice", BindingFlags.SetProperty, null, voice, new object[] { token });
                                }
                            }
                            catch { }
                        }
                        voiceType.InvokeMember("Speak", BindingFlags.InvokeMethod, null, voice, new object[] { text, 0 });
                    }
                }
                catch { }
            });
            t.IsBackground = true;
            t.SetApartmentState(ApartmentState.STA);
            t.Start();
        }

        private static string FormatSlovenianMinutesPhrase(int minutes)
        {
            if (minutes == 1) return "eno minuto";
            if (minutes == 2) return "dve minuti";
            if (minutes == 3) return "tri minute";
            if (minutes == 4) return "štiri minute";

            string word = NumberToSlovenianWord(minutes);
            int mod100 = minutes % 100;
            if (mod100 == 1) return word + " minuto";
            if (mod100 == 2) return word + " minuti";
            if (mod100 == 3 || mod100 == 4) return word + " minute";
            return word + " minut";
        }

        private static string NumberToSlovenianWord(int n)
        {
            string[] ones = new string[]
            {
                "nič", "ena", "dve", "tri", "štiri", "pet", "šest", "sedem", "osem", "devet",
                "deset", "enajst", "dvanajst", "trinajst", "štirinajst", "petnajst",
                "šestnajst", "sedemnajst", "osemnajst", "devetnajst"
            };
            string[] tens = new string[]
            {
                "", "deset", "dvajset", "trideset", "štirideset", "petdeset",
                "šestdeset", "sedemdeset", "osemdeset", "devetdeset"
            };

            if (n >= 0 && n < 20)
                return ones[n];
            if (n >= 20 && n < 100)
            {
                int t = n / 10;
                int u = n % 10;
                if (u == 0) return tens[t];
                string unitPrefix = (u == 1) ? "ena" : (u == 2 ? "dva" : ones[u]);
                return unitPrefix + "in" + tens[t];
            }
            return n.ToString(CultureInfo.InvariantCulture);
        }

        private static string FindSlovenianVoiceTokenPath()
        {
            string[] roots = new string[]
            {
                @"SOFTWARE\Microsoft\Speech_OneCore\Voices\Tokens",
                @"SOFTWARE\Microsoft\Speech\Voices\Tokens"
            };
            foreach (string root in roots)
            {
                try
                {
                    using (RegistryKey rk = Registry.LocalMachine.OpenSubKey(root))
                    {
                        if (rk == null) continue;
                        foreach (string sub in rk.GetSubKeyNames())
                        {
                            if (sub.IndexOf("slSI", StringComparison.OrdinalIgnoreCase) >= 0 ||
                                sub.IndexOf("Lado", StringComparison.OrdinalIgnoreCase) >= 0)
                            {
                                return @"HKEY_LOCAL_MACHINE\" + root + @"\" + sub;
                            }
                            using (RegistryKey attr = rk.OpenSubKey(sub + @"\Attributes"))
                            {
                                if (attr != null)
                                {
                                    string lang = Convert.ToString(attr.GetValue("Language"));
                                    if (lang == "424" || lang == "0424")
                                    {
                                        return @"HKEY_LOCAL_MACHINE\" + root + @"\" + sub;
                                    }
                                }
                            }
                        }
                    }
                }
                catch { }
            }
            return null;
        }

        private static void PlayWavBuffer(byte[] wavBytes)
        {
            if (!Enabled || wavBytes == null) return;
            ThreadPool.QueueUserWorkItem(_ =>
            {
                try
                {
                    lock (_playLock)
                    {
                        using (MemoryStream ms = new MemoryStream(wavBytes, false))
                        using (SoundPlayer player = new SoundPlayer(ms))
                        {
                            player.PlaySync();
                        }
                    }
                }
                catch { }
            });
        }

        private static byte[] BuildChimeWav(double[] frequencies, double noteDurationSec, double gapSec, bool brightBellTimbre)
        {
            const int sampleRate = 44100;
            int noteSamples = (int)(sampleRate * noteDurationSec);
            int gapSamples = (int)(sampleRate * gapSec);
            int tailSamples = (int)(sampleRate * 0.06);
            int totalSamples = frequencies.Length * noteSamples + Math.Max(0, frequencies.Length - 1) * gapSamples + tailSamples;

            short[] pcm = new short[totalSamples];

            for (int n = 0; n < frequencies.Length; n++)
            {
                double freq = frequencies[n];
                int startIdx = n * (noteSamples + gapSamples);
                int renderLen = Math.Min(noteSamples + tailSamples, totalSamples - startIdx);

                for (int i = 0; i < renderLen; i++)
                {
                    double t = (double)i / sampleRate;
                    // Smooth 6ms attack to prevent any click
                    double attack = (t < 0.006) ? (t / 0.006) : 1.0;
                    double sample;

                    if (brightBellTimbre)
                    {
                        double env1 = Math.Exp(-t * 7.5);
                        double env2 = Math.Exp(-t * 14.0);
                        double env3 = Math.Exp(-t * 22.0);
                        sample = 0.65 * env1 * Math.Sin(2.0 * Math.PI * freq * t)
                               + 0.25 * env2 * Math.Sin(2.0 * Math.PI * (freq * 2.0) * t)
                               + 0.10 * env3 * Math.Sin(2.0 * Math.PI * (freq * 3.0) * t);
                    }
                    else
                    {
                        double env1 = Math.Exp(-t * 6.8);
                        double env2 = Math.Exp(-t * 11.0);
                        sample = 0.62 * env1 * Math.Sin(2.0 * Math.PI * freq * t)
                               + 0.30 * env2 * Math.Sin(2.0 * Math.PI * (freq * 1.4983) * t)
                               + 0.08 * env2 * Math.Sin(2.0 * Math.PI * (freq * 0.5) * t);
                    }

                    double val = attack * sample * 21000.0;
                    int mixed = pcm[startIdx + i] + (int)val;
                    if (mixed > 32000) mixed = 32000;
                    else if (mixed < -32000) mixed = -32000;
                    pcm[startIdx + i] = (short)mixed;
                }
            }

            int dataBytes = totalSamples * 2;
            using (MemoryStream ms = new MemoryStream(44 + dataBytes))
            using (BinaryWriter bw = new BinaryWriter(ms))
            {
                bw.Write(Encoding.ASCII.GetBytes("RIFF"));
                bw.Write(36 + dataBytes);
                bw.Write(Encoding.ASCII.GetBytes("WAVE"));
                bw.Write(Encoding.ASCII.GetBytes("fmt "));
                bw.Write(16);
                bw.Write((short)1); // PCM
                bw.Write((short)1); // Mono
                bw.Write(sampleRate);
                bw.Write(sampleRate * 2);
                bw.Write((short)2);
                bw.Write((short)16);
                bw.Write(Encoding.ASCII.GetBytes("data"));
                bw.Write(dataBytes);
                for (int i = 0; i < totalSamples; i++)
                {
                    bw.Write(pcm[i]);
                }
                return ms.ToArray();
            }
        }
    }

    internal static class ThemePalette
    {
        public static readonly SolidColorBrush WindowBg = BrushFromHex("#0B0F17");
        public static readonly SolidColorBrush CardBg = BrushFromHex("#131A26");
        public static readonly SolidColorBrush CardBorder = BrushFromHex("#222E42");
        public static readonly SolidColorBrush SubCardBg = BrushFromHex("#0E141E");
        public static readonly SolidColorBrush Divider = BrushFromHex("#1F2A3C");

        public static readonly SolidColorBrush TextPrimary = BrushFromHex("#F8FAFC");
        public static readonly SolidColorBrush TextSecondary = BrushFromHex("#94A3B8");
        public static readonly SolidColorBrush TextMuted = BrushFromHex("#64748B");

        public static readonly SolidColorBrush AccentBlue = BrushFromHex("#38BDF8");
        public static readonly SolidColorBrush AccentBlueActiveBg = BrushFromHex("#162C46");
        public static readonly SolidColorBrush AccentBtnMuted = BrushFromHex("#3E5274");
        public static readonly SolidColorBrush AccentEmerald = BrushFromHex("#10B981");
        public static readonly SolidColorBrush AccentAmber = BrushFromHex("#F59E0B");
        public static readonly SolidColorBrush AccentCoral = BrushFromHex("#F43F5E");
        public static readonly SolidColorBrush AccentYellow = BrushFromHex("#FACC15");
        public static readonly SolidColorBrush AccentRed = BrushFromHex("#EF4444");

        public static readonly SolidColorBrush BtnBg = BrushFromHex("#1C2638");
        public static readonly SolidColorBrush BtnHoverBg = BrushFromHex("#26334B");
        public static readonly SolidColorBrush BtnPressedBg = BrushFromHex("#151D2B");
        public static readonly SolidColorBrush BtnBorder = BrushFromHex("#2E3D56");
        public static readonly SolidColorBrush BtnHoverBorder = BrushFromHex("#475E82");

        public static readonly SolidColorBrush TrackBg = BrushFromHex("#1C2638");

        public static SolidColorBrush BrushFromHex(string hex)
        {
            Color c = (Color)ColorConverter.ConvertFromString(hex);
            SolidColorBrush b = new SolidColorBrush(c);
            b.Freeze();
            return b;
        }
    }

    public class ModernActionButton : Border
    {
        public int CommandId { get; private set; }
        public event Action<ModernActionButton> Clicked;

        private readonly TextBlock _label;
        private bool _isHovered;
        private bool _isPressed;

        public ModernActionButton(int commandId, string text, Brush accentBrush, double fontSize = 14.0)
        {
            CommandId = commandId;
            CornerRadius = new CornerRadius(7);
            BorderThickness = new Thickness(1);
            Background = ThemePalette.BtnBg;
            BorderBrush = ThemePalette.BtnBorder;
            Cursor = Cursors.Hand;
            Margin = new Thickness(3);
            SnapsToDevicePixels = true;

            Grid innerGrid = new Grid();

            Border accentBar = new Border
            {
                Width = 3,
                HorizontalAlignment = HorizontalAlignment.Left,
                VerticalAlignment = VerticalAlignment.Stretch,
                CornerRadius = new CornerRadius(3, 0, 0, 3),
                Background = accentBrush,
                Opacity = 0.85
            };

            Viewbox labelScaler = new Viewbox
            {
                StretchDirection = StretchDirection.DownOnly,
                Stretch = Stretch.Uniform,
                Margin = new Thickness(8, 2, 6, 2),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };

            _label = new TextBlock
            {
                Text = text,
                Foreground = ThemePalette.TextPrimary,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = fontSize,
                FontWeight = FontWeights.SemiBold,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };

            labelScaler.Child = _label;
            innerGrid.Children.Add(accentBar);
            innerGrid.Children.Add(labelScaler);
            Child = innerGrid;

            MouseEnter += (s, e) => { _isHovered = true; UpdateVisualState(); };
            MouseLeave += (s, e) => { _isHovered = false; _isPressed = false; UpdateVisualState(); };
            MouseLeftButtonDown += (s, e) =>
            {
                _isPressed = true;
                UpdateVisualState();
                e.Handled = true;
            };
            MouseLeftButtonUp += (s, e) =>
            {
                if (_isPressed)
                {
                    _isPressed = false;
                    UpdateVisualState();
                    if (Clicked != null) Clicked(this);
                }
                e.Handled = true;
            };
        }

        private void UpdateVisualState()
        {
            if (_isPressed)
            {
                Background = ThemePalette.BtnPressedBg;
                BorderBrush = ThemePalette.AccentBlue;
                RenderTransform = new TranslateTransform(0, 1);
            }
            else if (_isHovered)
            {
                Background = ThemePalette.BtnHoverBg;
                BorderBrush = ThemePalette.BtnHoverBorder;
                RenderTransform = null;
            }
            else
            {
                Background = ThemePalette.BtnBg;
                BorderBrush = ThemePalette.BtnBorder;
                RenderTransform = null;
            }
        }
    }

    /// <summary>
    /// Compact icon toggle pill button for station header quick settings (Mute chimes / Dim view).
    /// </summary>
    public class HeaderQuickToggleButton : Border
    {
        private bool _isActive;
        private bool _isHovered;
        private readonly bool _isMuteButton;
        private readonly System.Windows.Shapes.Path _iconPath;
        private readonly string _tooltipActive;
        private readonly string _tooltipInactive;

        private static readonly Geometry GeomSpeakerOn = Geometry.Parse(
            "M 2,6 L 5,6 L 8.8,2.6 L 8.8,13.4 L 5,10 L 2,10 Z M 11.2,5.2 A 3.6,3.6 0 0 1 11.2,10.8 M 13.0,3.4 A 6.0,6.0 0 0 1 13.0,12.6");
        private static readonly Geometry GeomSpeakerMuted = Geometry.Parse(
            "M 2,6 L 5,6 L 8.8,2.6 L 8.8,13.4 L 5,10 L 2,10 Z M 11.2,5.5 L 15.0,10.5 M 15.0,5.5 L 11.2,10.5");
        private static readonly Geometry GeomDimMoon = Geometry.Parse(
            "M 10.6,2.4 A 5.5,5.5 0 1 0 13.6,10.4 A 4.4,4.4 0 0 1 10.6,2.4 Z");

        public event Action<bool> Toggled;

        public bool IsActive
        {
            get { return _isActive; }
            set
            {
                _isActive = value;
                UpdateVisuals();
            }
        }

        public HeaderQuickToggleButton(bool isMuteButton, bool initialActive, string tooltipInactive, string tooltipActive)
        {
            _isMuteButton = isMuteButton;
            _isActive = initialActive;
            _tooltipInactive = tooltipInactive;
            _tooltipActive = tooltipActive;

            Width = 28;
            Height = 22;
            CornerRadius = new CornerRadius(5);
            BorderThickness = new Thickness(1);
            Cursor = Cursors.Hand;
            SnapsToDevicePixels = true;
            VerticalAlignment = VerticalAlignment.Center;

            Canvas iconCanvas = new Canvas
            {
                Width = 16,
                Height = 16,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };

            _iconPath = new System.Windows.Shapes.Path
            {
                StrokeThickness = 1.5,
                StrokeStartLineCap = PenLineCap.Round,
                StrokeEndLineCap = PenLineCap.Round,
                StrokeLineJoin = PenLineJoin.Round
            };

            iconCanvas.Children.Add(_iconPath);
            Child = iconCanvas;

            MouseEnter += (s, e) => { _isHovered = true; UpdateVisuals(); };
            MouseLeave += (s, e) => { _isHovered = false; UpdateVisuals(); };
            MouseLeftButtonDown += (s, e) =>
            {
                e.Handled = true;
            };
            MouseLeftButtonUp += (s, e) =>
            {
                IsActive = !IsActive;
                if (Toggled != null) Toggled(_isActive);
                e.Handled = true;
            };

            UpdateVisuals();
        }

        private void UpdateVisuals()
        {
            ToolTip = _isActive ? _tooltipActive : _tooltipInactive;

            if (_isMuteButton)
            {
                _iconPath.Data = _isActive ? GeomSpeakerMuted : GeomSpeakerOn;
            }
            else
            {
                _iconPath.Data = GeomDimMoon;
            }

            if (_isActive)
            {
                Background = _isHovered ? ThemePalette.BtnHoverBg : ThemePalette.BtnPressedBg;
                BorderBrush = _isHovered ? ThemePalette.TextPrimary : ThemePalette.TextSecondary;
                _iconPath.Stroke = ThemePalette.TextPrimary;
            }
            else
            {
                Background = _isHovered ? ThemePalette.BtnHoverBg : ThemePalette.BtnBg;
                BorderBrush = _isHovered ? ThemePalette.BtnHoverBorder : ThemePalette.BtnBorder;
                _iconPath.Stroke = _isHovered ? ThemePalette.TextPrimary : ThemePalette.TextSecondary;
            }
        }
    }

    /// <summary>
    /// Station card that dynamically adapts between side-by-side and stacked layouts when the window is narrowed.
    /// </summary>
    public class StationCardControl : Border
    {
        public FunicularClient Client { get; private set; }
        public event Action QuickSettingsChanged;

        private readonly TextBlock _lblStation;
        private readonly StackPanel _statusPanel;
        private readonly Ellipse _statusDot;
        private readonly TextBlock _statusText;
        private readonly HeaderQuickToggleButton _btnMute;
        private readonly HeaderQuickToggleButton _btnDim;

        private readonly Grid _contentGrid;
        private readonly Grid _timeSection;
        private readonly Border _centerDivider;
        private readonly Grid _personsSection;

        private readonly TextBlock _txtTimerValue;
        private readonly TextBlock _txtSetTimeValue;
        private readonly Grid _bgIntervalGrid;
        private readonly TextBlock _txtBgIntervalValue;
        private readonly Border _timerProgressFill;
        private readonly Border _timerProgressTrack;

        private readonly TextBlock _txtPersonsCurrent;
        private readonly TextBlock _txtPersonsMax;
        private readonly Border _personsProgressFill;
        private readonly Border _personsProgressTrack;

        private readonly DispatcherTimer _countdownTimer;
        private readonly DispatcherTimer _pollTimer;

        private long _remainingMs = 0;
        private long _setMs = 0;
        private int _currentPersons = 0;
        private int _maxPersons = 0;
        private bool _isOnline = false;
        private bool _isPolling = false;
        private bool _isCommandBusy = false;
        private double _currentScale = 1.0;
        private bool _isNarrowLayout = false;
        private bool _hasInitialTelemetry = false;
        private int _lastTimeZone = -1;
        private int _lastPersonsZone = -1;
        private bool _isMuted = false;
        private bool _isDimmed = false;
        private bool _showIntervalBg = true;

        public bool IsMuted
        {
            get { return _isMuted; }
            set
            {
                _isMuted = value;
                if (_btnMute != null && _btnMute.IsActive != value)
                    _btnMute.IsActive = value;
            }
        }

        public bool IsDimmed
        {
            get { return _isDimmed; }
            set
            {
                _isDimmed = value;
                if (_btnDim != null && _btnDim.IsActive != value)
                    _btnDim.IsActive = value;
                ApplyDimVisuals();
            }
        }

        public bool ShowIntervalBg
        {
            get { return _showIntervalBg; }
            set
            {
                _showIntervalBg = value;
                if (_bgIntervalGrid != null)
                {
                    _bgIntervalGrid.Visibility = _showIntervalBg ? Visibility.Visible : Visibility.Collapsed;
                }
            }
        }

        public void ToggleIntervalBackground()
        {
            ShowIntervalBg = !ShowIntervalBg;
            if (QuickSettingsChanged != null) QuickSettingsChanged();
        }

        public StationCardControl(string stationName, string ipAddress, double initialScale = 1.0, bool initialMuted = false, bool initialDimmed = false, bool initialShowIntervalBg = true)
        {
            Client = new FunicularClient(ipAddress, 80);
            _currentScale = Math.Max(0.65, Math.Min(1.90, initialScale));
            _isMuted = initialMuted;
            _isDimmed = initialDimmed;
            _showIntervalBg = initialShowIntervalBg;

            Background = ThemePalette.CardBg;
            BorderBrush = ThemePalette.CardBorder;
            BorderThickness = new Thickness(1);
            CornerRadius = new CornerRadius(10);
            Padding = new Thickness(14, 10, 14, 12);
            SnapsToDevicePixels = true;

            Grid rootGrid = new Grid();
            rootGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(28, GridUnitType.Pixel) });
            rootGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

            // 1. Minimal Station Header
            Grid headerGrid = new Grid { Margin = new Thickness(2, 0, 2, 4) };

            _lblStation = new TextBlock
            {
                Text = stationName,
                Foreground = ThemePalette.TextPrimary,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 14.5,
                FontWeight = FontWeights.SemiBold,
                VerticalAlignment = VerticalAlignment.Center
            };

            StackPanel rightHeaderPanel = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right,
                VerticalAlignment = VerticalAlignment.Center
            };

            _btnMute = new HeaderQuickToggleButton(
                true,
                _isMuted,
                "Utišaj zvočna opozorila za to postajo",
                "Vklopi zvočna opozorila za to postajo")
            {
                Margin = new Thickness(0, 0, 6, 0)
            };
            _btnMute.Toggled += (muted) =>
            {
                _isMuted = muted;
                if (!_isMuted && ChimePlayer.Enabled)
                {
                    ChimePlayer.PlayTimeSingle();
                }
                if (QuickSettingsChanged != null) QuickSettingsChanged();
            };

            _btnDim = new HeaderQuickToggleButton(
                false,
                _isDimmed,
                "Zatemni prikaz te postaje",
                "Osvetli prikaz te postaje")
            {
                Margin = new Thickness(0, 0, 12, 0)
            };
            _btnDim.Toggled += (dimmed) =>
            {
                _isDimmed = dimmed;
                ApplyDimVisuals();
                if (QuickSettingsChanged != null) QuickSettingsChanged();
            };

            _statusPanel = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center
            };

            _statusDot = new Ellipse
            {
                Width = 7,
                Height = 7,
                Fill = ThemePalette.AccentAmber,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 6, 0)
            };

            _statusText = new TextBlock
            {
                Text = "Povezovanje",
                Foreground = ThemePalette.TextSecondary,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 11.5,
                VerticalAlignment = VerticalAlignment.Center
            };

            _statusPanel.Children.Add(_statusDot);
            _statusPanel.Children.Add(_statusText);

            rightHeaderPanel.Children.Add(_btnMute);
            rightHeaderPanel.Children.Add(_btnDim);
            rightHeaderPanel.Children.Add(_statusPanel);

            headerGrid.Children.Add(_lblStation);
            headerGrid.Children.Add(rightHeaderPanel);
            Grid.SetRow(headerGrid, 0);
            rootGrid.Children.Add(headerGrid);

            // 2. Adaptive Content Grid (Switches between Horizontal and Vertical layout based on width)
            _contentGrid = new Grid();

            // --- TIME SECTION ---
            _timeSection = new Grid();
            _timeSection.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1.1, GridUnitType.Star) });
            _timeSection.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1.0, GridUnitType.Star) });

            Border timeDisplayBox = new Border
            {
                Background = ThemePalette.SubCardBg,
                BorderBrush = ThemePalette.Divider,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(12, 6, 12, 8),
                Margin = new Thickness(3, 0, 3, 5)
            };

            Grid timeDisplayGrid = new Grid();
            timeDisplayGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            timeDisplayGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            timeDisplayGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

            // Background Interval Display (80% height, 80% transparency = Opacity 0.20)
            _bgIntervalGrid = new Grid
            {
                IsHitTestVisible = false,
                Opacity = 0.20,
                Visibility = _showIntervalBg ? Visibility.Visible : Visibility.Collapsed
            };
            _bgIntervalGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(0.1, GridUnitType.Star) });
            _bgIntervalGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(0.8, GridUnitType.Star) });
            _bgIntervalGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(0.1, GridUnitType.Star) });

            Viewbox bgIntervalViewbox = new Viewbox
            {
                Stretch = Stretch.Uniform,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            _txtBgIntervalValue = new TextBlock
            {
                Text = "--:--",
                Foreground = ThemePalette.TextSecondary,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontWeight = FontWeights.SemiBold,
                FontSize = 100,
                TextAlignment = TextAlignment.Center
            };
            Typography.SetNumeralAlignment(_txtBgIntervalValue, FontNumeralAlignment.Tabular);
            bgIntervalViewbox.Child = _txtBgIntervalValue;
            Grid.SetRow(bgIntervalViewbox, 1);
            _bgIntervalGrid.Children.Add(bgIntervalViewbox);
            Grid.SetRowSpan(_bgIntervalGrid, 3);
            timeDisplayGrid.Children.Add(_bgIntervalGrid);

            Grid timeTopRow = new Grid();
            TextBlock lblTimeHeader = new TextBlock
            {
                Text = "ČAS DO ODHODA",
                Foreground = ThemePalette.TextSecondary,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold
            };
            _txtSetTimeValue = new TextBlock
            {
                Text = "Interval: --:--",
                Foreground = ThemePalette.TextSecondary,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold,
                HorizontalAlignment = HorizontalAlignment.Right,
                Cursor = Cursors.Hand,
                ToolTip = "Kliknite za vklop / izklop prikaza intervala v ozadju"
            };
            _txtSetTimeValue.MouseLeftButtonDown += (s, e) =>
            {
                ToggleIntervalBackground();
                e.Handled = true;
            };
            _txtSetTimeValue.MouseEnter += (s, e) =>
            {
                _txtSetTimeValue.Foreground = ThemePalette.TextPrimary;
            };
            _txtSetTimeValue.MouseLeave += (s, e) =>
            {
                _txtSetTimeValue.Foreground = ThemePalette.TextSecondary;
            };
            timeTopRow.Children.Add(lblTimeHeader);
            timeTopRow.Children.Add(_txtSetTimeValue);
            Grid.SetRow(timeTopRow, 0);

            Viewbox timeViewbox = new Viewbox
            {
                StretchDirection = StretchDirection.DownOnly,
                Stretch = Stretch.Uniform,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 1, 0, 2)
            };
            _txtTimerValue = new TextBlock
            {
                Text = "--:--",
                Foreground = ThemePalette.TextPrimary,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 46,
                FontWeight = FontWeights.SemiBold,
                TextAlignment = TextAlignment.Center
            };
            Typography.SetNumeralAlignment(_txtTimerValue, FontNumeralAlignment.Tabular);
            timeViewbox.Child = _txtTimerValue;
            Grid.SetRow(timeViewbox, 1);

            _timerProgressTrack = new Border
            {
                Height = 4,
                CornerRadius = new CornerRadius(2),
                Background = ThemePalette.TrackBg,
                HorizontalAlignment = HorizontalAlignment.Stretch
            };
            _timerProgressFill = new Border
            {
                Height = 4,
                CornerRadius = new CornerRadius(2),
                Background = ThemePalette.AccentBtnMuted,
                HorizontalAlignment = HorizontalAlignment.Left,
                Width = 0
            };
            _timerProgressTrack.Child = _timerProgressFill;
            _timerProgressTrack.SizeChanged += (s, e) => UpdateProgressBars();
            Grid.SetRow(_timerProgressTrack, 2);

            timeDisplayGrid.Children.Add(timeTopRow);
            timeDisplayGrid.Children.Add(timeViewbox);
            timeDisplayGrid.Children.Add(_timerProgressTrack);
            timeDisplayBox.Child = timeDisplayGrid;
            Grid.SetRow(timeDisplayBox, 0);

            Grid timeButtonsGrid = new Grid();
            timeButtonsGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            timeButtonsGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            for (int c = 0; c < 3; c++)
                timeButtonsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            ModernActionButton btnInterval6 = new ModernActionButton(10, "6 min", ThemePalette.AccentBtnMuted)
            {
                ToolTip = "Nastavi interval na 6 minut (06:00)"
            };
            ModernActionButton btnInterval10 = new ModernActionButton(11, "10 min", ThemePalette.AccentBtnMuted)
            {
                ToolTip = "Nastavi interval na 10 minut (10:00)"
            };

            AddButtonToGrid(timeButtonsGrid, btnInterval10, 0, 0);
            AddButtonToGrid(timeButtonsGrid, new ModernActionButton(2, "+ 1 min", ThemePalette.AccentEmerald), 0, 1);
            AddButtonToGrid(timeButtonsGrid, new ModernActionButton(3, "+ 5 min", ThemePalette.AccentEmerald), 0, 2);

            AddButtonToGrid(timeButtonsGrid, btnInterval6, 1, 0);
            AddButtonToGrid(timeButtonsGrid, new ModernActionButton(5, "− 1 min", ThemePalette.AccentCoral), 1, 1);
            AddButtonToGrid(timeButtonsGrid, new ModernActionButton(6, "− 5 min", ThemePalette.AccentCoral), 1, 2);

            Grid.SetRow(timeButtonsGrid, 1);
            _timeSection.Children.Add(timeDisplayBox);
            _timeSection.Children.Add(timeButtonsGrid);

            // --- DIVIDER ---
            _centerDivider = new Border
            {
                Background = ThemePalette.Divider
            };

            // --- PERSONS SECTION ---
            _personsSection = new Grid();
            _personsSection.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1.1, GridUnitType.Star) });
            _personsSection.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1.0, GridUnitType.Star) });

            Border personsDisplayBox = new Border
            {
                Background = ThemePalette.SubCardBg,
                BorderBrush = ThemePalette.Divider,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(12, 6, 12, 8),
                Margin = new Thickness(3, 0, 3, 5)
            };

            Grid personsDisplayGrid = new Grid();
            personsDisplayGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            personsDisplayGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            personsDisplayGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

            TextBlock lblPersonsHeader = new TextBlock
            {
                Text = "ŠTEVILO OSEB",
                Foreground = ThemePalette.TextSecondary,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold
            };
            Grid.SetRow(lblPersonsHeader, 0);

            Viewbox personsViewbox = new Viewbox
            {
                StretchDirection = StretchDirection.DownOnly,
                Stretch = Stretch.Uniform,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 1, 0, 2)
            };

            StackPanel personsNumberStack = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };

            _txtPersonsCurrent = new TextBlock
            {
                Text = "--",
                Foreground = ThemePalette.TextPrimary,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 46,
                FontWeight = FontWeights.SemiBold
            };
            Typography.SetNumeralAlignment(_txtPersonsCurrent, FontNumeralAlignment.Tabular);

            _txtPersonsMax = new TextBlock
            {
                Text = " / --",
                Foreground = ThemePalette.TextMuted,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 24,
                VerticalAlignment = VerticalAlignment.Bottom,
                Margin = new Thickness(2, 0, 0, 5)
            };
            Typography.SetNumeralAlignment(_txtPersonsMax, FontNumeralAlignment.Tabular);

            personsNumberStack.Children.Add(_txtPersonsCurrent);
            personsNumberStack.Children.Add(_txtPersonsMax);
            personsViewbox.Child = personsNumberStack;
            Grid.SetRow(personsViewbox, 1);

            _personsProgressTrack = new Border
            {
                Height = 4,
                CornerRadius = new CornerRadius(2),
                Background = ThemePalette.TrackBg,
                HorizontalAlignment = HorizontalAlignment.Stretch
            };
            _personsProgressFill = new Border
            {
                Height = 4,
                CornerRadius = new CornerRadius(2),
                Background = ThemePalette.AccentBtnMuted,
                HorizontalAlignment = HorizontalAlignment.Left,
                Width = 0
            };
            _personsProgressTrack.Child = _personsProgressFill;
            _personsProgressTrack.SizeChanged += (s, e) => UpdateProgressBars();
            Grid.SetRow(_personsProgressTrack, 2);

            personsDisplayGrid.Children.Add(lblPersonsHeader);
            personsDisplayGrid.Children.Add(personsViewbox);
            personsDisplayGrid.Children.Add(_personsProgressTrack);
            personsDisplayBox.Child = personsDisplayGrid;
            Grid.SetRow(personsDisplayBox, 0);

            Grid personsButtonsGrid = new Grid();
            personsButtonsGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            personsButtonsGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            personsButtonsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            personsButtonsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            AddButtonToGrid(personsButtonsGrid, new ModernActionButton(8, "− 1 oseba", ThemePalette.AccentCoral, 14.5), 0, 0);
            AddButtonToGrid(personsButtonsGrid, new ModernActionButton(7, "+ 1 oseba", ThemePalette.AccentEmerald, 14.5), 0, 1);

            ModernActionButton btnReset = new ModernActionButton(9, "Ponastavi števec", ThemePalette.AccentBtnMuted, 14.0);
            btnReset.ToolTip = "Ponastavi število oseb na maksimalno kapaciteto (28)";
            btnReset.Clicked += async (btn) => await ExecuteCommandAsync(btn.CommandId);
            Grid.SetRow(btnReset, 1);
            Grid.SetColumn(btnReset, 0);
            Grid.SetColumnSpan(btnReset, 2);
            personsButtonsGrid.Children.Add(btnReset);

            Grid.SetRow(personsButtonsGrid, 1);
            _personsSection.Children.Add(personsDisplayBox);
            _personsSection.Children.Add(personsButtonsGrid);

            _contentGrid.Children.Add(_timeSection);
            _contentGrid.Children.Add(_centerDivider);
            _contentGrid.Children.Add(_personsSection);

            ApplyOrientationLayout(false);

            SizeChanged += (s, e) =>
            {
                bool shouldBeNarrow = e.NewSize.Width < 480;
                if (shouldBeNarrow != _isNarrowLayout)
                {
                    ApplyOrientationLayout(shouldBeNarrow);
                }
            };

            Grid.SetRow(_contentGrid, 1);
            rootGrid.Children.Add(_contentGrid);

            Child = rootGrid;

            _countdownTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(200) };
            _countdownTimer.Tick += (s, e) =>
            {
                if (_isOnline && _remainingMs > 0)
                {
                    _remainingMs = Math.Max(0, _remainingMs - 200);
                    RenderTime();
                }
            };
            _countdownTimer.Start();

            _pollTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(2000) };
            _pollTimer.Tick += async (s, e) => await PollStatusAsync(false);
            _pollTimer.Start();

            ApplyCounterScale(_currentScale);
            ApplyDimVisuals();
        }

        private void ApplyDimVisuals()
        {
            if (_contentGrid != null)
                _contentGrid.Opacity = _isDimmed ? 0.18 : 1.0;
            if (_lblStation != null)
                _lblStation.Opacity = _isDimmed ? 0.38 : 1.0;
            if (_statusPanel != null)
                _statusPanel.Opacity = _isDimmed ? 0.38 : 1.0;
            Background = _isDimmed ? ThemePalette.SubCardBg : ThemePalette.CardBg;
        }

        public void ApplyCounterScale(double scale)
        {
            _currentScale = Math.Max(0.65, Math.Min(1.90, scale));
            _txtTimerValue.FontSize = Math.Max(26, Math.Round(46 * _currentScale));
            _txtPersonsCurrent.FontSize = Math.Max(26, Math.Round(46 * _currentScale));
            _txtPersonsMax.FontSize = Math.Max(14, Math.Round(24 * _currentScale));
            UpdateProgressBars();
        }

        private void ApplyOrientationLayout(bool narrowVertical)
        {
            _isNarrowLayout = narrowVertical;
            _contentGrid.ColumnDefinitions.Clear();
            _contentGrid.RowDefinitions.Clear();

            if (narrowVertical)
            {
                // Stacked layout for narrow window widths (< 480px)
                _contentGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
                _contentGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(10, GridUnitType.Pixel) });
                _contentGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

                Grid.SetRow(_timeSection, 0);
                Grid.SetColumn(_timeSection, 0);

                _centerDivider.Height = 1;
                _centerDivider.Width = double.NaN;
                _centerDivider.HorizontalAlignment = HorizontalAlignment.Stretch;
                _centerDivider.VerticalAlignment = VerticalAlignment.Center;
                _centerDivider.Margin = new Thickness(4, 0, 4, 0);
                Grid.SetRow(_centerDivider, 1);
                Grid.SetColumn(_centerDivider, 0);

                Grid.SetRow(_personsSection, 2);
                Grid.SetColumn(_personsSection, 0);
            }
            else
            {
                // Side-by-side layout for standard/wide widths (>= 480px)
                _contentGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1.12, GridUnitType.Star) });
                _contentGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(18, GridUnitType.Pixel) });
                _contentGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1.0, GridUnitType.Star) });

                Grid.SetRow(_timeSection, 0);
                Grid.SetColumn(_timeSection, 0);

                _centerDivider.Width = 1;
                _centerDivider.Height = double.NaN;
                _centerDivider.HorizontalAlignment = HorizontalAlignment.Center;
                _centerDivider.VerticalAlignment = VerticalAlignment.Stretch;
                _centerDivider.Margin = new Thickness(0, 4, 0, 4);
                Grid.SetRow(_centerDivider, 0);
                Grid.SetColumn(_centerDivider, 1);

                Grid.SetRow(_personsSection, 0);
                Grid.SetColumn(_personsSection, 2);
            }

            if (_timeSection.RowDefinitions.Count >= 2)
                _timeSection.RowDefinitions[0].Height = new GridLength(1.1, GridUnitType.Star);
            if (_personsSection.RowDefinitions.Count >= 2)
                _personsSection.RowDefinitions[0].Height = new GridLength(1.1, GridUnitType.Star);
        }

        private void AddButtonToGrid(Grid grid, ModernActionButton btn, int row, int col)
        {
            btn.Clicked += async (b) => await ExecuteCommandAsync(b.CommandId);
            Grid.SetRow(btn, row);
            Grid.SetColumn(btn, col);
            grid.Children.Add(btn);
        }

        private async Task ExecuteCommandAsync(int commandId)
        {
            if (_isCommandBusy) return;
            _isCommandBusy = true;
            try
            {
                if (commandId == 9)
                {
                    const int targetMax = 28;
                    StationTelemetry t = await Client.SendOperatorCommandAsync(0, 1800);
                    int attempts = 0;
                    while (t.MaxPersons != targetMax && attempts < 35)
                    {
                        attempts++;
                        int adjCmd = (t.MaxPersons < targetMax) ? 7 : 8;
                        await Task.Delay(40);
                        bool stepFailed = false;
                        try
                        {
                            t = await Client.SendOperatorCommandAsync(adjCmd, 1500);
                            ApplyTelemetry(t, false);
                        }
                        catch
                        {
                            stepFailed = true;
                        }
                        if (stepFailed)
                        {
                            await Task.Delay(120);
                        }
                    }
                    ApplyTelemetry(t, true);
                }
                else if (commandId == 10 || commandId == 11)
                {
                    long targetSetMs = (commandId == 10) ? 360000L : 600000L;
                    StationTelemetry t = await Client.SendOperatorCommandAsync(0, 1800);
                    int attempts = 0;
                    while (Math.Abs(t.SetTimeMs - targetSetMs) >= 7500 && attempts < 30)
                    {
                        attempts++;
                        long diff = targetSetMs - t.SetTimeMs;
                        int adjCmd;
                        if (diff >= 210000) adjCmd = 3;         // + 5 min (+300s)
                        else if (diff <= -210000) adjCmd = 6;   // - 5 min (-300s)
                        else if (diff >= 37500) adjCmd = 2;     // + 1 min (+60s)
                        else if (diff <= -37500) adjCmd = 5;    // - 1 min (-60s)
                        else if (diff > 0) adjCmd = 1;          // + 15 s
                        else adjCmd = 4;                        // - 15 s

                        await Task.Delay(40);
                        bool stepFailed = false;
                        try
                        {
                            t = await Client.SendOperatorCommandAsync(adjCmd, 1500);
                            ApplyTelemetry(t, true);
                        }
                        catch
                        {
                            stepFailed = true;
                        }
                        if (stepFailed)
                        {
                            await Task.Delay(120);
                        }
                    }
                    ApplyTelemetry(t, true);
                }
                else
                {
                    StationTelemetry t = await Client.SendOperatorCommandAsync(commandId, 1800);
                    ApplyTelemetry(t, true);
                }
            }
            catch
            {
                SetOfflineState();
            }
            finally
            {
                _isCommandBusy = false;
            }
        }

        public async Task PollStatusAsync(bool forceSyncTime)
        {
            if (_isPolling || _isCommandBusy) return;
            _isPolling = true;
            try
            {
                StationTelemetry t = await Client.SendOperatorCommandAsync(0, 1800);
                ApplyTelemetry(t, forceSyncTime);
            }
            catch
            {
                SetOfflineState();
            }
            finally
            {
                _isPolling = false;
            }
        }

        private void ApplyTelemetry(StationTelemetry t, bool forceSyncTime)
        {
            bool isFirstLoad = !_hasInitialTelemetry;
            bool wasOffline = !_isOnline;
            _isOnline = true;

            _statusDot.Fill = ThemePalette.AccentEmerald;
            _statusText.Text = "Povezano";
            _statusText.Foreground = ThemePalette.TextSecondary;

            long prevRemainingMs = _remainingMs;
            long prevSetMs = _setMs;
            int prevPersons = _currentPersons;

            _setMs = t.SetTimeMs;
            if (forceSyncTime || wasOffline || Math.Abs(t.RemainingTimeMs - _remainingMs) > 2000)
            {
                _remainingMs = t.RemainingTimeMs;
            }

            _currentPersons = t.CurrentPersons;
            _maxPersons = t.MaxPersons;

            if (!isFirstLoad && !wasOffline && !forceSyncTime && !_isCommandBusy)
            {
                bool clockReset = (t.SetTimeMs > 0) &&
                                  (t.SetTimeMs == prevSetMs) &&
                                  (t.RemainingTimeMs - prevRemainingMs > 15000) &&
                                  (t.RemainingTimeMs >= t.SetTimeMs - 20000);
                if (clockReset && (t.CurrentPersons == 0 || t.CurrentPersons < prevPersons) && !_isMuted)
                {
                    int nextMinutes = (int)((t.SetTimeMs + 30000) / 60000);
                    if (nextMinutes <= 0) nextMinutes = 1;
                    ChimePlayer.SpeakDeparture(nextMinutes);
                }
            }

            RenderTime(isFirstLoad);
            RenderPersons(isFirstLoad);
            _hasInitialTelemetry = true;
        }

        private void SetOfflineState()
        {
            _isOnline = false;
            _statusDot.Fill = ThemePalette.AccentCoral;
            _statusText.Text = "Ni povezave";
            _statusText.Foreground = ThemePalette.AccentCoral;

            _txtTimerValue.Text = "--:--";
            _txtTimerValue.Foreground = ThemePalette.TextMuted;
            if (_txtBgIntervalValue != null)
                _txtBgIntervalValue.Text = "--:--";
            _txtPersonsCurrent.Text = "--";
            _txtPersonsCurrent.Foreground = ThemePalette.TextMuted;
            _txtPersonsMax.Text = " / --";
            _timerProgressFill.Width = 0;
            _personsProgressFill.Width = 0;
        }

        private void RenderTime(bool suppressChime = false)
        {
            if (!_isOnline) return;

            string formattedTime = FormatMinSec(_remainingMs);
            string formattedInterval = FormatMinSec(_setMs);
            _txtTimerValue.Text = formattedTime;
            _txtSetTimeValue.Text = "Interval: " + formattedInterval;
            if (_txtBgIntervalValue != null)
                _txtBgIntervalValue.Text = formattedInterval;

            int timeZone;
            if (_remainingMs <= 0 || formattedTime == "00:00")
            {
                timeZone = 2;
                _txtTimerValue.Foreground = ThemePalette.AccentRed;
                _timerProgressFill.Background = ThemePalette.AccentRed;
            }
            else if (_remainingMs <= 60000)
            {
                timeZone = 1;
                _txtTimerValue.Foreground = ThemePalette.AccentAmber;
                _timerProgressFill.Background = ThemePalette.AccentAmber;
            }
            else
            {
                timeZone = 0;
                _txtTimerValue.Foreground = ThemePalette.TextPrimary;
                _timerProgressFill.Background = ThemePalette.AccentBtnMuted;
            }

            if (!suppressChime && !_isMuted && _lastTimeZone >= 0 && timeZone > _lastTimeZone)
            {
                if (timeZone == 1)
                    ChimePlayer.PlayTimeSingle();
                else if (timeZone == 2)
                    ChimePlayer.PlayTimeTriple();
            }
            _lastTimeZone = timeZone;

            UpdateProgressBars();
        }

        private void RenderPersons(bool suppressChime = false)
        {
            if (!_isOnline) return;

            _txtPersonsCurrent.Text = _currentPersons.ToString("00", CultureInfo.InvariantCulture);
            _txtPersonsMax.Text = " / " + _maxPersons.ToString("00", CultureInfo.InvariantCulture);

            int personsZone;
            if (_maxPersons > 0 && _currentPersons >= _maxPersons)
            {
                personsZone = 2;
                _txtPersonsCurrent.Foreground = ThemePalette.AccentCoral;
                _personsProgressFill.Background = ThemePalette.AccentCoral;
            }
            else if (_maxPersons > 0 && (double)_currentPersons / _maxPersons >= 0.85)
            {
                personsZone = 1;
                _txtPersonsCurrent.Foreground = ThemePalette.AccentAmber;
                _personsProgressFill.Background = ThemePalette.AccentAmber;
            }
            else
            {
                personsZone = 0;
                _txtPersonsCurrent.Foreground = ThemePalette.TextPrimary;
                _personsProgressFill.Background = ThemePalette.AccentBtnMuted;
            }

            if (!suppressChime && !_isMuted && _lastPersonsZone >= 0 && personsZone > _lastPersonsZone)
            {
                if (personsZone == 1)
                    ChimePlayer.PlayPersonsSingle();
                else if (personsZone == 2)
                    ChimePlayer.PlayPersonsTriple();
            }
            _lastPersonsZone = personsZone;

            UpdateProgressBars();
        }

        private void UpdateProgressBars()
        {
            if (!_isOnline) return;

            double timeTrackW = _timerProgressTrack.ActualWidth;
            if (timeTrackW > 0)
            {
                double ratio = _setMs > 0 ? Math.Max(0.0, Math.Min(1.0, (double)_remainingMs / _setMs)) : 0.0;
                _timerProgressFill.Width = timeTrackW * ratio;
            }

            double personsTrackW = _personsProgressTrack.ActualWidth;
            if (personsTrackW > 0)
            {
                double ratio = _maxPersons > 0 ? Math.Max(0.0, Math.Min(1.0, (double)_currentPersons / _maxPersons)) : 0.0;
                _personsProgressFill.Width = personsTrackW * ratio;
            }
        }

        private static string FormatMinSec(long ms)
        {
            if (ms < 0) ms = 0;
            long totalSeconds = (ms + 499) / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            return string.Format(CultureInfo.InvariantCulture, "{0:00}:{1:00}", minutes, seconds);
        }
    }

    public class MainWindow : Window
    {
        private const int WM_SYSCOMMAND = 0x0112;
        private const uint MF_STRING = 0x0000;
        private const uint MF_SEPARATOR = 0x0800;
        private const uint MF_CHECKED = 0x0008;
        private const uint MF_UNCHECKED = 0x0000;
        private const uint MF_BYCOMMAND = 0x0000;
        private const uint SC_CLOSE = 0xF060;
        private const int IDM_ALWAYS_ON_TOP = 0x1001;
        private const int IDM_START_WITH_WINDOWS = 0x1002;
        private const int IDM_SOUND_ENABLED = 0x1003;
        private const int IDM_VOICE_ENABLED = 0x1004;
        private const int IDM_ZOOM_IN = 0x1010;
        private const int IDM_ZOOM_OUT = 0x1011;
        private const int IDM_ZOOM_RESET = 0x1012;
        private const int IDM_TOGGLE_CONTROLS = 0x1013;
        private const int IDM_VERSION = 0x1020;

        private const int DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19;
        private const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
        private const int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
        private const int DWMWA_BORDER_COLOR = 34;
        private const int DWMWA_CAPTION_COLOR = 35;
        private const int DWMWCP_ROUND = 2;

        [DllImport("dwmapi.dll", PreserveSig = true)]
        private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

        [DllImport("user32.dll")]
        private static extern IntPtr GetSystemMenu(IntPtr hWnd, bool bRevert);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern bool InsertMenu(IntPtr hMenu, uint uPosition, uint uFlags, UIntPtr uIDNewItem, string lpNewItem);

        [DllImport("user32.dll")]
        private static extern uint CheckMenuItem(IntPtr hMenu, uint uIDCheckItem, uint uCheck);

        private readonly AppConfig _config;
        private readonly StationCardControl _cardZgoraj;
        private readonly StationCardControl _cardSpodaj;
        private IntPtr _hSysMenu = IntPtr.Zero;
        private bool _isInitialized = false;

        public MainWindow()
        {
            _config = AppConfig.Load();
            AppConfig.ApplyAutostart(_config.StartWithWindows);
            ChimePlayer.Enabled = _config.SoundEnabled;
            ChimePlayer.VoiceEnabled = _config.VoiceAnnouncementEnabled;

            Title = "Vzpenjača";
            Width = _config.WindowWidth;
            Height = _config.WindowHeight;
            MinWidth = 280;
            MinHeight = 460;
            Topmost = _config.AlwaysOnTop;
            ApplyWindowChrome();

            if (IsPositionValid(_config.WindowLeft, _config.WindowTop, _config.WindowWidth, _config.WindowHeight))
            {
                WindowStartupLocation = WindowStartupLocation.Manual;
                Left = _config.WindowLeft;
                Top = _config.WindowTop;
            }
            else
            {
                WindowStartupLocation = WindowStartupLocation.CenterScreen;
            }

            Background = ThemePalette.WindowBg;
            UseLayoutRounding = true;
            SnapsToDevicePixels = true;
            TextOptions.SetTextFormattingMode(this, TextFormattingMode.Ideal);
            TextOptions.SetTextRenderingMode(this, TextRenderingMode.ClearType);

            Grid mainGrid = new Grid
            {
                Margin = new Thickness(12)
            };
            mainGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            mainGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(10, GridUnitType.Pixel) });
            mainGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

            _cardZgoraj = new StationCardControl("ZGORNJA POSTAJA", _config.IpZgoraj, _config.CounterScale, _config.MuteZgoraj, _config.DimZgoraj, _config.ShowIntervalBgZgoraj);
            _cardZgoraj.QuickSettingsChanged += () =>
            {
                _config.MuteZgoraj = _cardZgoraj.IsMuted;
                _config.DimZgoraj = _cardZgoraj.IsDimmed;
                _config.ShowIntervalBgZgoraj = _cardZgoraj.ShowIntervalBg;
                _config.Save();
            };
            Grid.SetRow(_cardZgoraj, 0);

            _cardSpodaj = new StationCardControl("SPODNJA POSTAJA", _config.IpSpodaj, _config.CounterScale, _config.MuteSpodaj, _config.DimSpodaj, _config.ShowIntervalBgSpodaj);
            _cardSpodaj.QuickSettingsChanged += () =>
            {
                _config.MuteSpodaj = _cardSpodaj.IsMuted;
                _config.DimSpodaj = _cardSpodaj.IsDimmed;
                _config.ShowIntervalBgSpodaj = _cardSpodaj.ShowIntervalBg;
                _config.Save();
            };
            Grid.SetRow(_cardSpodaj, 2);

            mainGrid.Children.Add(_cardZgoraj);
            mainGrid.Children.Add(_cardSpodaj);

            Content = mainGrid;

            SourceInitialized += OnSourceInitialized;

            LocationChanged += (s, e) => SaveWindowBounds();
            SizeChanged += (s, e) => SaveWindowBounds();
            Closing += (s, e) => SaveWindowBounds();

            MouseLeftButtonDown += (s, e) =>
            {
                if (WindowStyle == WindowStyle.None && e.ButtonState == MouseButtonState.Pressed)
                {
                    DragMove();
                }
            };

            MouseRightButtonUp += (s, e) =>
            {
                if (WindowStyle == WindowStyle.None)
                {
                    Point p = PointToScreen(e.GetPosition(this));
                    SystemCommands.ShowSystemMenu(this, p);
                    e.Handled = true;
                }
            };

            KeyDown += (s, e) =>
            {
                if (e.Key == Key.F11)
                {
                    ToggleWindowControls();
                    e.Handled = true;
                    return;
                }

                if ((Keyboard.Modifiers & ModifierKeys.Control) == ModifierKeys.Control)
                {
                    if (e.Key == Key.OemPlus || e.Key == Key.Add)
                    {
                        AdjustScale(0.1);
                        e.Handled = true;
                    }
                    else if (e.Key == Key.OemMinus || e.Key == Key.Subtract)
                    {
                        AdjustScale(-0.1);
                        e.Handled = true;
                    }
                    else if (e.Key == Key.D0 || e.Key == Key.NumPad0)
                    {
                        SetScale(1.0);
                        e.Handled = true;
                    }
                }
            };

            PreviewMouseWheel += (s, e) =>
            {
                if ((Keyboard.Modifiers & ModifierKeys.Control) == ModifierKeys.Control)
                {
                    if (e.Delta > 0)
                        AdjustScale(0.08);
                    else if (e.Delta < 0)
                        AdjustScale(-0.08);
                    e.Handled = true;
                }
            };

            Loaded += async (s, e) =>
            {
                _isInitialized = true;
                SaveWindowBounds();
                await Task.WhenAll(
                    _cardZgoraj.PollStatusAsync(true),
                    _cardSpodaj.PollStatusAsync(true));
            };
        }

        private static bool IsPositionValid(double left, double top, double width, double height)
        {
            if (double.IsNaN(left) || double.IsNaN(top) || double.IsInfinity(left) || double.IsInfinity(top))
                return false;

            double vLeft = SystemParameters.VirtualScreenLeft;
            double vTop = SystemParameters.VirtualScreenTop;
            double vRight = vLeft + SystemParameters.VirtualScreenWidth;
            double vBottom = vTop + SystemParameters.VirtualScreenHeight;

            return (left + width > vLeft + 40) &&
                   (left < vRight - 40) &&
                   (top >= vTop - 10) &&
                   (top < vBottom - 40);
        }

        private void SaveWindowBounds()
        {
            if (!_isInitialized) return;
            if (WindowState == WindowState.Normal)
            {
                _config.WindowLeft = Left;
                _config.WindowTop = Top;
                _config.WindowWidth = Width;
                _config.WindowHeight = Height;
                _config.Save();
            }
        }

        private void AdjustScale(double delta)
        {
            SetScale(Math.Round(Math.Max(0.65, Math.Min(1.90, _config.CounterScale + delta)), 2));
        }

        private void SetScale(double newScale)
        {
            _config.CounterScale = newScale;
            _cardZgoraj.ApplyCounterScale(newScale);
            _cardSpodaj.ApplyCounterScale(newScale);
            _config.Save();
        }

        private void ApplyWindowChrome()
        {
            if (_config.HideWindowControls)
            {
                WindowChrome chrome = new WindowChrome
                {
                    CaptionHeight = 0,
                    ResizeBorderThickness = new Thickness(4),
                    GlassFrameThickness = new Thickness(-1),
                    CornerRadius = new CornerRadius(0),
                    UseAeroCaptionButtons = false
                };
                WindowChrome.SetWindowChrome(this, chrome);
                WindowStyle = WindowStyle.None;
            }
            else
            {
                WindowChrome.SetWindowChrome(this, null);
                WindowStyle = WindowStyle.SingleBorderWindow;
            }
        }

        public void ToggleWindowControls()
        {
            _config.HideWindowControls = !_config.HideWindowControls;
            ApplyWindowChrome();
            _config.Save();
            RefreshSystemMenu();
        }

        private void OnSourceInitialized(object sender, EventArgs e)
        {
            RefreshSystemMenu();

            IntPtr hwnd = new WindowInteropHelper(this).Handle;
            if (hwnd != IntPtr.Zero)
            {
                try
                {
                    int dark = 1;
                    DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, ref dark, sizeof(int));
                    DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1, ref dark, sizeof(int));
                    int darkBorder = 0x002A170F; // BGR for #0F172A
                    DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR, ref darkBorder, sizeof(int));
                    DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, ref darkBorder, sizeof(int));
                    int roundPref = DWMWCP_ROUND;
                    DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, ref roundPref, sizeof(int));
                }
                catch { }

                HwndSource source = HwndSource.FromHwnd(hwnd);
                if (source != null)
                {
                    source.AddHook(WndProc);
                }
            }
        }

        private void RefreshSystemMenu()
        {
            IntPtr hwnd = new WindowInteropHelper(this).Handle;
            if (hwnd == IntPtr.Zero) return;
            GetSystemMenu(hwnd, true);
            _hSysMenu = GetSystemMenu(hwnd, false);
            if (_hSysMenu != IntPtr.Zero)
            {
                uint topFlag = _config.AlwaysOnTop ? MF_CHECKED : MF_UNCHECKED;
                uint autostartFlag = _config.StartWithWindows ? MF_CHECKED : MF_UNCHECKED;
                uint soundFlag = _config.SoundEnabled ? MF_CHECKED : MF_UNCHECKED;
                uint voiceFlag = _config.VoiceAnnouncementEnabled ? MF_CHECKED : MF_UNCHECKED;
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | topFlag, (UIntPtr)IDM_ALWAYS_ON_TOP, "Vedno na vrhu");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | autostartFlag, (UIntPtr)IDM_START_WITH_WINDOWS, "Zaženi ob zagonu računalnika");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | soundFlag, (UIntPtr)IDM_SOUND_ENABLED, "Zvočna opozorila");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | voiceFlag, (UIntPtr)IDM_VOICE_ENABLED, "Glasovno obvestilo ob odhodu");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING, (UIntPtr)IDM_TOGGLE_CONTROLS, "Preklopi kontrole okna\t(F11)");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_SEPARATOR, UIntPtr.Zero, string.Empty);
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING, (UIntPtr)IDM_ZOOM_IN, "Povečaj števce\t(Ctrl +)");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING, (UIntPtr)IDM_ZOOM_OUT, "Pomanjšaj števce\t(Ctrl -)");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING, (UIntPtr)IDM_ZOOM_RESET, "Ponastavi velikost števcev\t(Ctrl 0)");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_SEPARATOR, UIntPtr.Zero, string.Empty);
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING, (UIntPtr)IDM_VERSION, "Različica 1.0.2");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_SEPARATOR, UIntPtr.Zero, string.Empty);
            }
        }

        private IntPtr WndProc(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
        {
            if (msg == WM_SYSCOMMAND)
            {
                int cmd = wParam.ToInt32() & 0xFFFF;
                if (cmd == IDM_ALWAYS_ON_TOP)
                {
                    Topmost = !Topmost;
                    _config.AlwaysOnTop = Topmost;
                    _config.Save();

                    if (_hSysMenu != IntPtr.Zero)
                    {
                        CheckMenuItem(_hSysMenu, IDM_ALWAYS_ON_TOP, MF_BYCOMMAND | (Topmost ? MF_CHECKED : MF_UNCHECKED));
                    }
                    handled = true;
                }
                else if (cmd == IDM_START_WITH_WINDOWS)
                {
                    _config.StartWithWindows = !_config.StartWithWindows;
                    AppConfig.ApplyAutostart(_config.StartWithWindows);
                    _config.Save();

                    if (_hSysMenu != IntPtr.Zero)
                    {
                        CheckMenuItem(_hSysMenu, IDM_START_WITH_WINDOWS, MF_BYCOMMAND | (_config.StartWithWindows ? MF_CHECKED : MF_UNCHECKED));
                    }
                    handled = true;
                }
                else if (cmd == IDM_SOUND_ENABLED)
                {
                    _config.SoundEnabled = !_config.SoundEnabled;
                    ChimePlayer.Enabled = _config.SoundEnabled;
                    _config.Save();

                    if (_hSysMenu != IntPtr.Zero)
                    {
                        CheckMenuItem(_hSysMenu, IDM_SOUND_ENABLED, MF_BYCOMMAND | (_config.SoundEnabled ? MF_CHECKED : MF_UNCHECKED));
                    }
                    if (_config.SoundEnabled)
                    {
                        ChimePlayer.PlayTimeSingle();
                    }
                    handled = true;
                }
                else if (cmd == IDM_VOICE_ENABLED)
                {
                    _config.VoiceAnnouncementEnabled = !_config.VoiceAnnouncementEnabled;
                    ChimePlayer.VoiceEnabled = _config.VoiceAnnouncementEnabled;
                    _config.Save();

                    if (_hSysMenu != IntPtr.Zero)
                    {
                        CheckMenuItem(_hSysMenu, IDM_VOICE_ENABLED, MF_BYCOMMAND | (_config.VoiceAnnouncementEnabled ? MF_CHECKED : MF_UNCHECKED));
                    }
                    handled = true;
                }
                else if (cmd == IDM_TOGGLE_CONTROLS)
                {
                    ToggleWindowControls();
                    handled = true;
                }
                else if (cmd == IDM_ZOOM_IN)
                {
                    AdjustScale(0.1);
                    handled = true;
                }
                else if (cmd == IDM_ZOOM_OUT)
                {
                    AdjustScale(-0.1);
                    handled = true;
                }
                else if (cmd == IDM_ZOOM_RESET)
                {
                    SetScale(1.0);
                    handled = true;
                }
                else if (cmd == IDM_VERSION)
                {
                    MessageBox.Show(
                        "Nadzorna plošča vzpenjače\nRazličica 1.0.2\n\nZgornja postaja: " + _config.IpZgoraj + "\nSpodnja postaja: " + _config.IpSpodaj,
                        "O programu — Vzpenjača",
                        MessageBoxButton.OK,
                        MessageBoxImage.Information);
                    handled = true;
                }
            }
            return IntPtr.Zero;
        }
    }

    static class Program
    {
        [STAThread]
        static void Main()
        {
            Application app = new Application();
            app.Run(new MainWindow());
        }
    }
}
