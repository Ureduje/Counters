using System;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Media;
using System.Net;
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
using Forms = System.Windows.Forms;
using Drawing = System.Drawing;

[assembly: AssemblyTitle("Vzpenjača")]
[assembly: AssemblyDescription("Nadzorna plošča vzpenjače — Zgornja in spodnja postaja")]
[assembly: AssemblyCompany("Vzpenjača")]
[assembly: AssemblyProduct("Vzpenjača")]
[assembly: AssemblyVersion("1.2.3.0")]
[assembly: AssemblyFileVersion("1.2.3.0")]

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
        public const string DefaultVoiceTemplate = "Vzpenjača je odpeljala. Naslednja vožnja je čez {minute}.";
        public string VoiceAnnouncementTemplate = DefaultVoiceTemplate;
        public double VoiceAnnouncementGain = 2.0;
        public bool MuteZgoraj = false;
        public bool MuteSpodaj = false;
        public bool DimZgoraj = false;
        public bool DimSpodaj = false;
        public bool ShowIntervalBgZgoraj = true;
        public bool ShowIntervalBgSpodaj = true;
        public bool ShowCapacityZgoraj = true;
        public bool ShowCapacitySpodaj = true;
        public int CounterViewMode = 0;
        public bool ShowFunicularTrack = true;
        public bool FunicularTrackReversed = false;
        public int RideDurationSec = 90;
        public bool CloudRelayEnabled = true;
        public string CloudRelayServer = "https://vzpenjaca-kontrola-default-rtdb.europe-west1.firebasedatabase.app";
        public string CloudChannelId = "vzpenjaca-3436";
        public string CloudPin = "";

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
                        else if (key.Equals("VoiceAnnouncementTemplate", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrEmpty(val))
                        {
                            cfg.VoiceAnnouncementTemplate = val;
                        }
                        else if (key.Equals("VoiceAnnouncementGain", StringComparison.OrdinalIgnoreCase))
                        {
                            double vg;
                            if (double.TryParse(val, NumberStyles.Float, CultureInfo.InvariantCulture, out vg) && vg >= 0.5 && vg <= 5.0)
                                cfg.VoiceAnnouncementGain = vg;
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
                        else if (key.Equals("ShowCapacityZgoraj", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.ShowCapacityZgoraj = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("ShowCapacitySpodaj", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.ShowCapacitySpodaj = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("CounterViewMode", StringComparison.OrdinalIgnoreCase))
                        {
                            int cvm;
                            if (int.TryParse(val, NumberStyles.Integer, CultureInfo.InvariantCulture, out cvm) && (cvm == 0 || cvm == 1))
                                cfg.CounterViewMode = cvm;
                        }
                        else if (key.Equals("ShowFunicularTrack", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.ShowFunicularTrack = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("FunicularTrackReversed", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.FunicularTrackReversed = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("RideDurationSec", StringComparison.OrdinalIgnoreCase))
                        {
                            int rds;
                            if (int.TryParse(val, NumberStyles.Integer, CultureInfo.InvariantCulture, out rds) && rds >= 10 && rds <= 600)
                                cfg.RideDurationSec = rds;
                        }
                        else if (key.Equals("CloudRelayEnabled", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.CloudRelayEnabled = val.Equals("true", StringComparison.OrdinalIgnoreCase) || val == "1";
                        }
                        else if (key.Equals("CloudRelayServer", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrEmpty(val))
                        {
                            string srv = val.TrimEnd('/');
                            if (srv.Equals("https://ntfy.sh", StringComparison.OrdinalIgnoreCase) ||
                                srv.Equals("http://ntfy.sh", StringComparison.OrdinalIgnoreCase))
                            {
                                srv = "https://ntfy.adminforge.de";
                            }
                            cfg.CloudRelayServer = srv;
                        }
                        else if (key.Equals("CloudChannelId", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrEmpty(val))
                        {
                            cfg.CloudChannelId = val;
                        }
                        else if (key.Equals("CloudPin", StringComparison.OrdinalIgnoreCase))
                        {
                            cfg.CloudPin = val;
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
                sb.AppendLine("VoiceAnnouncementTemplate=" + (VoiceAnnouncementTemplate ?? DefaultVoiceTemplate).Replace("\r", " ").Replace("\n", " ").Trim());
                sb.AppendLine("VoiceAnnouncementGain=" + VoiceAnnouncementGain.ToString("0.00", CultureInfo.InvariantCulture));
                sb.AppendLine("MuteZgoraj=" + (MuteZgoraj ? "true" : "false"));
                sb.AppendLine("MuteSpodaj=" + (MuteSpodaj ? "true" : "false"));
                sb.AppendLine("DimZgoraj=" + (DimZgoraj ? "true" : "false"));
                sb.AppendLine("DimSpodaj=" + (DimSpodaj ? "true" : "false"));
                sb.AppendLine("ShowIntervalBgZgoraj=" + (ShowIntervalBgZgoraj ? "true" : "false"));
                sb.AppendLine("ShowIntervalBgSpodaj=" + (ShowIntervalBgSpodaj ? "true" : "false"));
                sb.AppendLine("ShowCapacityZgoraj=" + (ShowCapacityZgoraj ? "true" : "false"));
                sb.AppendLine("ShowCapacitySpodaj=" + (ShowCapacitySpodaj ? "true" : "false"));
                sb.AppendLine("CounterViewMode=" + CounterViewMode.ToString(CultureInfo.InvariantCulture));
                sb.AppendLine("ShowFunicularTrack=" + (ShowFunicularTrack ? "true" : "false"));
                sb.AppendLine("FunicularTrackReversed=" + (FunicularTrackReversed ? "true" : "false"));
                sb.AppendLine("RideDurationSec=" + RideDurationSec.ToString(CultureInfo.InvariantCulture));
                sb.AppendLine("CloudRelayEnabled=" + (CloudRelayEnabled ? "true" : "false"));
                sb.AppendLine("CloudRelayServer=" + CloudRelayServer);
                sb.AppendLine("CloudChannelId=" + CloudChannelId);
                sb.AppendLine("CloudPin=" + CloudPin);
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
        public static string VoiceTemplate = AppConfig.DefaultVoiceTemplate;
        public static double VoiceGain = 2.0;

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

        public static string BuildAnnouncementText(string template, int minutes)
        {
            if (minutes <= 0) minutes = 1;
            string tpl = string.IsNullOrWhiteSpace(template)
                ? AppConfig.DefaultVoiceTemplate
                : template.Trim();

            string phrase = FormatSlovenianMinutesPhrase(minutes);
            return ReplaceCaseInsensitive(
                ReplaceCaseInsensitive(tpl, "{minute}", phrase),
                "{min}", phrase);
        }

        private static string ReplaceCaseInsensitive(string input, string token, string replacement)
        {
            if (string.IsNullOrEmpty(input) || string.IsNullOrEmpty(token)) return input;
            int idx = input.IndexOf(token, StringComparison.OrdinalIgnoreCase);
            if (idx < 0) return input;
            StringBuilder sb = new StringBuilder();
            int lastIdx = 0;
            while (idx >= 0)
            {
                sb.Append(input, lastIdx, idx - lastIdx);
                sb.Append(replacement);
                lastIdx = idx + token.Length;
                idx = input.IndexOf(token, lastIdx, StringComparison.OrdinalIgnoreCase);
            }
            sb.Append(input, lastIdx, input.Length - lastIdx);
            return sb.ToString();
        }

        public static void SpeakDeparture(int minutes)
        {
            if (!VoiceEnabled) return;
            DateTime now = DateTime.UtcNow;
            if ((now - _lastDepartureSpeechUtc).TotalSeconds < 4.0) return;
            _lastDepartureSpeechUtc = now;

            string text = BuildAnnouncementText(VoiceTemplate, minutes);
            SpeakCustomText(text, VoiceGain);
        }

        public static void SpeakCustomText(string text, double gainMultiplier)
        {
            if (string.IsNullOrWhiteSpace(text)) return;
            double gain = Math.Max(0.5, Math.Min(5.0, gainMultiplier));

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

                        try
                        {
                            voiceType.InvokeMember("Volume", BindingFlags.SetProperty, null, voice, new object[] { 100 });
                        }
                        catch { }

                        bool playedAmplified = false;
                        try
                        {
                            Type memStreamType = Type.GetTypeFromProgID("SAPI.SpMemoryStream");
                            if (memStreamType != null)
                            {
                                object memStream = Activator.CreateInstance(memStreamType);
                                object fmt = memStreamType.InvokeMember("Format", BindingFlags.GetProperty, null, memStream, null);
                                if (fmt != null)
                                {
                                    // 22 = SAFT22kHz16BitMono
                                    fmt.GetType().InvokeMember("Type", BindingFlags.SetProperty, null, fmt, new object[] { 22 });
                                }
                                voiceType.InvokeMember("AudioOutputStream", BindingFlags.SetProperty, null, voice, new object[] { memStream });
                                voiceType.InvokeMember("Speak", BindingFlags.InvokeMethod, null, voice, new object[] { text, 0 });
                                object rawObj = memStreamType.InvokeMember("GetData", BindingFlags.InvokeMethod, null, memStream, null);
                                byte[] pcm = rawObj as byte[];
                                if (pcm != null && pcm.Length > 0)
                                {
                                    byte[] wav = BuildAmplifiedSpeechWav(pcm, 22050, gain);
                                    using (MemoryStream ms = new MemoryStream(wav, false))
                                    using (SoundPlayer player = new SoundPlayer(ms))
                                    {
                                        player.PlaySync();
                                    }
                                    playedAmplified = true;
                                }
                            }
                        }
                        catch { }

                        if (!playedAmplified)
                        {
                            voiceType.InvokeMember("AudioOutputStream", BindingFlags.SetProperty, null, voice, new object[] { null });
                            voiceType.InvokeMember("Speak", BindingFlags.InvokeMethod, null, voice, new object[] { text, 0 });
                        }
                    }
                }
                catch { }
            });
            t.IsBackground = true;
            t.SetApartmentState(ApartmentState.STA);
            t.Start();
        }

        private static byte[] BuildAmplifiedSpeechWav(byte[] pcmBytes, int sampleRate, double gainMultiplier)
        {
            int sampleCount = pcmBytes.Length / 2;
            int peak = 1;
            for (int i = 0; i < sampleCount; i++)
            {
                short s = BitConverter.ToInt16(pcmBytes, i * 2);
                int abs = (s == short.MinValue) ? 32767 : Math.Abs((int)s);
                if (abs > peak) peak = abs;
            }

            // Normalize to full scale (32000) and apply gain multiplier with soft-knee limiting
            double normScale = (32000.0 / peak) * gainMultiplier;

            int dataBytes = sampleCount * 2;
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

                for (int i = 0; i < sampleCount; i++)
                {
                    short s = BitConverter.ToInt16(pcmBytes, i * 2);
                    double amplified = s * normScale;
                    if (amplified > 26000.0)
                    {
                        amplified = 26000.0 + 6700.0 * Math.Tanh((amplified - 26000.0) / 6700.0);
                    }
                    else if (amplified < -26000.0)
                    {
                        amplified = -26000.0 - 6700.0 * Math.Tanh((-amplified - 26000.0) / 6700.0);
                    }
                    int outVal = (int)Math.Round(amplified);
                    if (outVal > 32760) outVal = 32760;
                    else if (outVal < -32760) outVal = -32760;
                    bw.Write((short)outVal);
                }
                return ms.ToArray();
            }
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
        public static readonly SolidColorBrush AccentCoral = BrushFromHex("#EF4444");
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
                TextAlignment = TextAlignment.Center,
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
    /// Horizontal real-time visualization of the single funicular car on tracks,
    /// ported from Android's FunicularTrackView and placed between Zgornja and Spodnja station cards.
    /// </summary>
    public class FunicularTrackControl : Border
    {
        private static readonly Pen SleeperPen = CreateFrozenPen(ThemePalette.Divider, 1.3, PenLineCap.Round, PenLineJoin.Round);
        private static readonly Pen RailPen = CreateFrozenPen(ThemePalette.BtnBorder, 2.2, PenLineCap.Round, PenLineJoin.Round);
        private static readonly Pen ActiveTrailEmeraldPen = CreateFrozenPen(ThemePalette.AccentEmerald, 2.4, PenLineCap.Round, PenLineJoin.Round);
        private static readonly Pen ActiveTrailBluePen = CreateFrozenPen(ThemePalette.AccentBlue, 2.4, PenLineCap.Round, PenLineJoin.Round);
        private static readonly Pen CarStrokeEmeraldPen = CreateFrozenPen(ThemePalette.AccentEmerald, 1.5, PenLineCap.Round, PenLineJoin.Round);
        private static readonly Pen CarStrokeBluePen = CreateFrozenPen(ThemePalette.AccentBlue, 1.5, PenLineCap.Round, PenLineJoin.Round);
        private static readonly Pen CarStrokeIdlePen = CreateFrozenPen(ThemePalette.TextSecondary, 1.5, PenLineCap.Round, PenLineJoin.Round);

        private static readonly SolidColorBrush WinOnlineRidingBrush = CreateAlphaBrush("#F8FAFC", 225);
        private static readonly SolidColorBrush WinOnlineDockedBrush = CreateAlphaBrush("#F8FAFC", 150);
        private static readonly SolidColorBrush WinOfflineBrush = CreateAlphaBrush("#64748B", 150);

        private static readonly Typeface LabelTypeface = new Typeface(
            new FontFamily("Segoe UI Semibold, Segoe UI"),
            FontStyles.Normal,
            FontWeights.SemiBold,
            FontStretches.Normal);

        private static readonly Typeface StatusTypeface = new Typeface(
            new FontFamily("Consolas, Segoe UI Semibold, Segoe UI"),
            FontStyles.Normal,
            FontWeights.Bold,
            FontStretches.Normal);

        private readonly DispatcherTimer _animTimer;

        private bool _isZgornjaToSpodnja = false;
        private bool _isOnline = false;
        private bool _isRiding = false;
        private bool _isFromSpodnja = true;
        private bool _leftToRight = true;
        private double _targetPos = 0.0;
        private double _displayPos = 0.0;
        private long _rideElapsedMs = 0L;
        private int _totalRideDurationSec = 90;
        private bool _hasInitializedPos = false;

        public event Action DirectionToggleRequested;
        public event Action DirectionToggled;

        public bool IsZgornjaToSpodnja
        {
            get { return _isZgornjaToSpodnja; }
            set
            {
                if (_isZgornjaToSpodnja != value)
                {
                    _isZgornjaToSpodnja = value;
                    _targetPos = 1.0 - _targetPos;
                    _displayPos = 1.0 - _displayPos;
                    _leftToRight = _isZgornjaToSpodnja ? !_isFromSpodnja : _isFromSpodnja;
                    InvalidateVisual();
                }
            }
        }

        public void ToggleDirection()
        {
            IsZgornjaToSpodnja = !IsZgornjaToSpodnja;
            if (DirectionToggled != null) DirectionToggled();
        }

        public FunicularTrackControl(bool initialZgornjaToSpodnja = false)
        {
            _isZgornjaToSpodnja = initialZgornjaToSpodnja;
            Background = ThemePalette.CardBg;
            BorderBrush = ThemePalette.CardBorder;
            BorderThickness = new Thickness(1);
            CornerRadius = new CornerRadius(8);
            SnapsToDevicePixels = true;
            UseLayoutRounding = true;
            Cursor = Cursors.Hand;
            ToolTip = "Položaj vzpenjače v realnem času (kliknite za menjavo smeri Spodnja ↔ Zgornja)";

            _animTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(33) };
            _animTimer.Tick += (s, e) =>
            {
                if (_isRiding && Math.Abs(_targetPos - _displayPos) > 0.0004)
                {
                    _displayPos += (_targetPos - _displayPos) * 0.25;
                    if (Math.Abs(_targetPos - _displayPos) <= 0.0004)
                    {
                        _displayPos = _targetPos;
                        _animTimer.Stop();
                    }
                    InvalidateVisual();
                }
                else
                {
                    _displayPos = _targetPos;
                    _animTimer.Stop();
                    InvalidateVisual();
                }
            };

            MouseEnter += (s, e) =>
            {
                BorderBrush = ThemePalette.BtnHoverBorder;
            };

            MouseLeave += (s, e) =>
            {
                BorderBrush = ThemePalette.CardBorder;
            };

            MouseLeftButtonDown += (s, e) =>
            {
                e.Handled = true;
                Dispatcher.BeginInvoke(new Action(() =>
                {
                    if (DirectionToggleRequested != null)
                        DirectionToggleRequested();
                    else
                        ToggleDirection();
                }));
            };

            MouseLeftButtonUp += (s, e) =>
            {
                e.Handled = true;
            };
        }

        public void SetRideState(
            bool online,
            bool riding,
            bool isFromSpodnja,
            double canonicalFraction,
            long elapsedMs,
            int totalDurationSec = 90)
        {
            double viewPos = _isZgornjaToSpodnja ? (1.0 - canonicalFraction) : canonicalFraction;
            double clamped = Math.Max(0.0, Math.Min(1.0, viewPos));
            _isOnline = online;
            _isRiding = riding;
            _isFromSpodnja = isFromSpodnja;
            _leftToRight = _isZgornjaToSpodnja ? !isFromSpodnja : isFromSpodnja;
            _targetPos = clamped;
            _rideElapsedMs = elapsedMs;
            _totalRideDurationSec = Math.Max(10, totalDurationSec);

            if (!_hasInitializedPos || !riding || Math.Abs(clamped - _displayPos) > 0.15)
            {
                _displayPos = clamped;
                _hasInitializedPos = true;
            }

            if (_isRiding && Math.Abs(_targetPos - _displayPos) > 0.0004)
            {
                if (!_animTimer.IsEnabled)
                    _animTimer.Start();
            }
            else
            {
                _displayPos = _targetPos;
                if (_animTimer.IsEnabled)
                    _animTimer.Stop();
            }

            InvalidateVisual();
        }

        protected override void OnRender(DrawingContext dc)
        {
            base.OnRender(dc);
            double w = ActualWidth;
            double h = ActualHeight;
            if (w <= 2.0 || h <= 2.0) return;

            double padX = 14.0;
            double headerBaselineY = h * 0.33;
            double labelFontSize = Math.Max(9.5, Math.Min(11.5, h * 0.22));

            bool isHeadingToZgornja = _isFromSpodnja;
            string leftLabelText = _isZgornjaToSpodnja ? "ZGORNJA" : "SPODNJA";
            string rightLabelText = _isZgornjaToSpodnja ? "SPODNJA" : "ZGORNJA";

            bool leftActive = _isRiding && (_isZgornjaToSpodnja ? isHeadingToZgornja : !isHeadingToZgornja);
            Brush leftActiveBrush = _isZgornjaToSpodnja ? ThemePalette.AccentEmerald : ThemePalette.AccentBlue;
            Brush leftBrush = !_isOnline
                ? ThemePalette.TextMuted
                : (leftActive ? leftActiveBrush : ThemePalette.TextSecondary);

            FormattedText ftLeft = CreateFormattedText(leftLabelText, LabelTypeface, labelFontSize, leftBrush);
            dc.DrawText(ftLeft, new Point(padX, headerBaselineY - ftLeft.Baseline));

            bool rightActive = _isRiding && (_isZgornjaToSpodnja ? !isHeadingToZgornja : isHeadingToZgornja);
            Brush rightActiveBrush = _isZgornjaToSpodnja ? ThemePalette.AccentBlue : ThemePalette.AccentEmerald;
            Brush rightBrush = !_isOnline
                ? ThemePalette.TextMuted
                : (rightActive ? rightActiveBrush : ThemePalette.TextSecondary);

            FormattedText ftRight = CreateFormattedText(rightLabelText, LabelTypeface, labelFontSize, rightBrush);
            dc.DrawText(ftRight, new Point(w - padX - ftRight.Width, headerBaselineY - ftRight.Baseline));

            if (_isRiding)
            {
                long maxSec = _totalRideDurationSec;
                long sec = Math.Max(0L, Math.Min(maxSec, _rideElapsedMs / 1000L));
                string statusStr = (_leftToRight ? "▶ " : "◀ ") +
                                   sec.ToString(CultureInfo.InvariantCulture) + "s / " +
                                   maxSec.ToString(CultureInfo.InvariantCulture) + "s";
                Brush statusBrush = isHeadingToZgornja ? ThemePalette.AccentEmerald : ThemePalette.AccentBlue;
                FormattedText ftStatus = CreateFormattedText(statusStr, StatusTypeface, labelFontSize, statusBrush);
                dc.DrawText(ftStatus, new Point((w - ftStatus.Width) * 0.5, headerBaselineY - ftStatus.Baseline));
            }

            // 2. Horizontal Railway Track
            double carHalfW = Math.Max(12.0, Math.Min(17.0, h * 0.29));
            double carH = Math.Max(12.0, Math.Min(17.0, h * 0.32));
            double trackStartX = padX + carHalfW + 4.0;
            double trackEndX = w - padX - carHalfW - 4.0;
            if (trackEndX <= trackStartX) return;

            double railY = h * 0.84;

            // Track sleepers / crossties underneath the rail
            double sleeperTop = railY - 1.5;
            double sleeperBot = railY + 3.5;
            int numSleepers = Math.Max(18, Math.Min(36, (int)Math.Round((trackEndX - trackStartX) / 20.0)));
            for (int i = 0; i <= numSleepers; i++)
            {
                double tx = trackStartX + (trackEndX - trackStartX) * ((double)i / (double)numSleepers);
                dc.DrawLine(SleeperPen, new Point(tx, sleeperTop), new Point(tx, sleeperBot));
            }

            // Main steel rail line
            dc.DrawLine(RailPen, new Point(trackStartX, railY), new Point(trackEndX, railY));

            double carX = trackStartX + _displayPos * (trackEndX - trackStartX);
            Brush activeBrush;
            Pen activeTrailPen;
            Pen activeCarPen;
            if (!_isOnline)
            {
                activeBrush = ThemePalette.TextMuted;
                activeTrailPen = RailPen;
                activeCarPen = CarStrokeIdlePen;
            }
            else if (_isRiding && isHeadingToZgornja)
            {
                activeBrush = ThemePalette.AccentEmerald;
                activeTrailPen = ActiveTrailEmeraldPen;
                activeCarPen = CarStrokeEmeraldPen;
            }
            else if (_isRiding && !isHeadingToZgornja)
            {
                activeBrush = ThemePalette.AccentBlue;
                activeTrailPen = ActiveTrailBluePen;
                activeCarPen = CarStrokeBluePen;
            }
            else
            {
                activeBrush = ThemePalette.TextSecondary;
                activeTrailPen = RailPen;
                activeCarPen = CarStrokeIdlePen;
            }

            // Active trail along the track behind the funicular car while riding
            if (_isOnline && _isRiding)
            {
                double originX = _leftToRight ? trackStartX : trackEndX;
                dc.DrawLine(activeTrailPen, new Point(originX, railY), new Point(carX, railY));
            }

            // Left & Right Station Track Buffers / Stops
            double stationNodeR = 3.5;
            Brush leftNodeBrush = (_isOnline && _isRiding && !_leftToRight) ? activeBrush : ThemePalette.BtnBorder;
            dc.DrawEllipse(leftNodeBrush, null, new Point(trackStartX, railY), stationNodeR, stationNodeR);

            Brush rightNodeBrush = (_isOnline && _isRiding && _leftToRight) ? activeBrush : ThemePalette.BtnBorder;
            dc.DrawEllipse(rightNodeBrush, null, new Point(trackEndX, railY), stationNodeR, stationNodeR);

            // 3. Funicular Car sitting directly on tracks
            double wheelR = 2.1;
            double carBottom = railY - wheelR * 0.85;
            double carTop = carBottom - carH;
            Rect carRect = new Rect(carX - carHalfW, carTop, carHalfW * 2.0, carH);

            // Undercarriage bogie / wheels on the rail
            Brush wheelBrush = (_isOnline && _isRiding) ? activeBrush : ThemePalette.TextMuted;
            dc.DrawEllipse(wheelBrush, null, new Point(carX - carHalfW * 0.54, railY - wheelR * 0.25), wheelR, wheelR);
            dc.DrawEllipse(wheelBrush, null, new Point(carX + carHalfW * 0.54, railY - wheelR * 0.25), wheelR, wheelR);

            // Cabin body fill + border
            Brush cabinFill = (_isOnline && _isRiding) ? ThemePalette.AccentBlueActiveBg : ThemePalette.BtnBg;
            Pen cabinStroke = (_isOnline && _isRiding) ? activeCarPen : CarStrokeIdlePen;
            dc.DrawRoundedRectangle(cabinFill, cabinStroke, carRect, 3.8, 3.8);

            // Two panoramic cabin windows
            double winMarginX = carHalfW * 0.22;
            double winGap = carHalfW * 0.14;
            double winTop = carTop + (carBottom - carTop) * 0.22;
            double winBottom = carTop + (carBottom - carTop) * 0.64;
            double winW = Math.Max(1.0, carHalfW - winMarginX - winGap * 0.5);
            double winH = Math.Max(1.0, winBottom - winTop);
            Rect winRect1 = new Rect(carX - carHalfW + winMarginX, winTop, winW, winH);
            Rect winRect2 = new Rect(carX + winGap * 0.5, winTop, winW, winH);
            Brush winBrush = !_isOnline
                ? WinOfflineBrush
                : (_isRiding ? WinOnlineRidingBrush : WinOnlineDockedBrush);
            dc.DrawRoundedRectangle(winBrush, null, winRect1, 1.5, 1.5);
            dc.DrawRoundedRectangle(winBrush, null, winRect2, 1.5, 1.5);

            // Direction chevron in front of the cabin when moving
            if (_isOnline && _isRiding)
            {
                double dir = _leftToRight ? 1.0 : -1.0;
                double arrowBaseX = carX + dir * (carHalfW + 5.0);
                double arrowTipX = arrowBaseX + dir * 4.2;
                double arrowMidY = (carTop + carBottom) * 0.5;
                double arrowHalfH = 3.5;
                if (arrowTipX >= trackStartX + 2.0 && arrowTipX <= trackEndX - 2.0)
                {
                    StreamGeometry arrowGeom = new StreamGeometry();
                    using (StreamGeometryContext ctx = arrowGeom.Open())
                    {
                        ctx.BeginFigure(new Point(arrowBaseX, arrowMidY - arrowHalfH), false, false);
                        ctx.LineTo(new Point(arrowTipX, arrowMidY), true, false);
                        ctx.LineTo(new Point(arrowBaseX, arrowMidY + arrowHalfH), true, false);
                    }
                    arrowGeom.Freeze();
                    dc.DrawGeometry(null, activeCarPen, arrowGeom);
                }
            }
        }

        private static FormattedText CreateFormattedText(string text, Typeface typeface, double fontSize, Brush brush)
        {
            return new FormattedText(
                text,
                CultureInfo.InvariantCulture,
                FlowDirection.LeftToRight,
                typeface,
                fontSize,
                brush);
        }

        private static Pen CreateFrozenPen(Brush brush, double thickness, PenLineCap cap, PenLineJoin join)
        {
            Pen p = new Pen(brush, thickness)
            {
                StartLineCap = cap,
                EndLineCap = cap,
                LineJoin = join
            };
            p.Freeze();
            return p;
        }

        private static SolidColorBrush CreateAlphaBrush(string hex, byte alpha)
        {
            Color baseColor = (Color)ColorConverter.ConvertFromString(hex);
            Color c = Color.FromArgb(alpha, baseColor.R, baseColor.G, baseColor.B);
            SolidColorBrush b = new SolidColorBrush(c);
            b.Freeze();
            return b;
        }
    }

    /// <summary>
    /// Station card that dynamically adapts between side-by-side and stacked layouts when the window is narrowed.
    /// </summary>
    public class StationCardControl : Border
    {
        public FunicularClient Client { get; private set; }
        public event Action QuickSettingsChanged;
        public event Action<bool> TelemetryUpdated;
        public event Action StateTicked;
        public event Action DepartureDetected;

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

        private readonly StackPanel _timeNumberStack;
        private readonly TextBlock _txtTimerValue;
        private readonly TextBlock _txtTimerIntervalInline;
        private readonly TextBlock _txtSetTimeValue;
        private readonly Grid _bgIntervalGrid;
        private readonly TextBlock _txtBgIntervalValue;
        private readonly TextBlock _txtUnderIntervalValue;
        private readonly Border _timerProgressFill;
        private readonly Border _timerProgressTrack;

        private readonly StackPanel _personsNumberStack;
        private readonly TextBlock _txtPersonsCurrent;
        private readonly TextBlock _txtPersonsMax;
        private readonly TextBlock _txtSetCapacityValue;
        private readonly TextBlock _txtUnderCapacityValue;
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
        private readonly SemaphoreSlim _commandLock = new SemaphoreSlim(1, 1);
        private double _currentScale = 1.0;
        private bool _isNarrowLayout = false;
        private bool _hasInitialTelemetry = false;
        private int _lastTimeZone = -1;
        private int _lastPersonsZone = -1;
        private bool _isMuted = false;
        private bool _isDimmed = false;
        private bool _showIntervalBg = true;
        private bool _showCapacityUnder = true;
        private int _counterViewMode = 0;
        private double _extraVerticalHeightReduction = 0.0;

        public bool IsOnline { get { return _isOnline; } }
        public int CurrentPersons { get { return _currentPersons; } }
        public int MaxPersons { get { return _maxPersons; } }
        public long RemainingMs { get { return _remainingMs; } }
        public long SetMs { get { return _setMs; } }

        public double ExtraVerticalHeightReduction
        {
            get { return _extraVerticalHeightReduction; }
            set
            {
                if (Math.Abs(_extraVerticalHeightReduction - value) > 0.01)
                {
                    _extraVerticalHeightReduction = value;
                    UpdateSectionRowHeights();
                }
            }
        }

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
                UpdateCounterViewVisuals();
            }
        }

        public bool ShowCapacityUnder
        {
            get { return _showCapacityUnder; }
            set
            {
                _showCapacityUnder = value;
                UpdateCounterViewVisuals();
            }
        }

        public int CounterViewMode
        {
            get { return _counterViewMode; }
            set
            {
                _counterViewMode = (value == 1) ? 1 : 0;
                UpdateCounterViewVisuals();
            }
        }

        public void ToggleIntervalBackground()
        {
            ShowIntervalBg = !ShowIntervalBg;
            if (QuickSettingsChanged != null) QuickSettingsChanged();
        }

        public void ToggleCapacityUnder()
        {
            ShowCapacityUnder = !ShowCapacityUnder;
            if (QuickSettingsChanged != null) QuickSettingsChanged();
        }

        public Task ExecuteRemoteCommandAsync(int commandId)
        {
            TaskCompletionSource<bool> tcs = new TaskCompletionSource<bool>();
            Dispatcher.BeginInvoke(new Action(async () =>
            {
                try
                {
                    await ExecuteCommandAsync(commandId, true);
                    tcs.TrySetResult(true);
                }
                catch (Exception ex)
                {
                    tcs.TrySetException(ex);
                }
            }));
            return tcs.Task;
        }

        public StationCardControl(
            string stationName,
            string ipAddress,
            double initialScale = 1.0,
            bool initialMuted = false,
            bool initialDimmed = false,
            bool initialShowIntervalBg = true,
            bool initialShowCapacityUnder = true,
            int initialCounterViewMode = 0)
        {
            Client = new FunicularClient(ipAddress, 80);
            _currentScale = Math.Max(0.65, Math.Min(1.90, initialScale));
            _isMuted = initialMuted;
            _isDimmed = initialDimmed;
            _showIntervalBg = initialShowIntervalBg;
            _showCapacityUnder = initialShowCapacityUnder;
            _counterViewMode = (initialCounterViewMode == 1) ? 1 : 0;

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
                Padding = new Thickness(0),
                Margin = new Thickness(3, 0, 3, 5),
                ClipToBounds = true
            };

            Grid timeDisplayGrid = new Grid();
            timeDisplayGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            timeDisplayGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            timeDisplayGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

            // Background Interval Display (Always 80% width, 80% transparency = Opacity 0.20)
            _bgIntervalGrid = new Grid
            {
                IsHitTestVisible = false,
                Opacity = 0.20,
                Visibility = (_counterViewMode == 0 && _showIntervalBg) ? Visibility.Visible : Visibility.Collapsed
            };
            _bgIntervalGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(0.1, GridUnitType.Star) });
            _bgIntervalGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(0.8, GridUnitType.Star) });
            _bgIntervalGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(0.1, GridUnitType.Star) });

            Viewbox bgIntervalViewbox = new Viewbox
            {
                Stretch = Stretch.Uniform,
                HorizontalAlignment = HorizontalAlignment.Stretch,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, -500, 0, -500)
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
            Grid.SetColumn(bgIntervalViewbox, 1);
            _bgIntervalGrid.Children.Add(bgIntervalViewbox);
            Grid.SetRowSpan(_bgIntervalGrid, 3);
            timeDisplayGrid.Children.Add(_bgIntervalGrid);

            Grid timeTopRow = new Grid
            {
                Margin = new Thickness(12, 6, 12, 0)
            };
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
                Margin = new Thickness(12, 1, 12, 2)
            };
            StackPanel timeVerticalStack = new StackPanel
            {
                Orientation = Orientation.Vertical,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };

            _timeNumberStack = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };

            _txtTimerValue = new TextBlock
            {
                Text = "--:--",
                Foreground = ThemePalette.TextPrimary,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 46,
                FontWeight = FontWeights.SemiBold,
                TextAlignment = TextAlignment.Center,
                HorizontalAlignment = HorizontalAlignment.Center
            };
            Typography.SetNumeralAlignment(_txtTimerValue, FontNumeralAlignment.Tabular);

            _txtTimerIntervalInline = new TextBlock
            {
                Text = "/--",
                Foreground = ThemePalette.TextMuted,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 46,
                FontWeight = FontWeights.Light,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0),
                Visibility = (_counterViewMode == 0 && _showIntervalBg) ? Visibility.Visible : Visibility.Collapsed
            };
            Typography.SetNumeralAlignment(_txtTimerIntervalInline, FontNumeralAlignment.Tabular);

            _txtUnderIntervalValue = new TextBlock
            {
                Text = "--:--",
                Foreground = ThemePalette.TextMuted,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 46,
                FontWeight = FontWeights.Normal,
                Opacity = 0.25,
                TextAlignment = TextAlignment.Center,
                HorizontalAlignment = HorizontalAlignment.Center,
                Visibility = (_counterViewMode == 1 && _showIntervalBg) ? Visibility.Visible : Visibility.Collapsed
            };
            Typography.SetNumeralAlignment(_txtUnderIntervalValue, FontNumeralAlignment.Tabular);

            _timeNumberStack.Children.Add(_txtTimerValue);
            _timeNumberStack.Children.Add(_txtTimerIntervalInline);
            timeVerticalStack.Children.Add(_timeNumberStack);
            timeVerticalStack.Children.Add(_txtUnderIntervalValue);
            timeViewbox.Child = timeVerticalStack;
            Grid.SetRow(timeViewbox, 1);

            _timerProgressTrack = new Border
            {
                Height = 4,
                CornerRadius = new CornerRadius(2),
                Background = ThemePalette.TrackBg,
                HorizontalAlignment = HorizontalAlignment.Stretch,
                Margin = new Thickness(12, 0, 12, 8)
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

            AddButtonToGrid(timeButtonsGrid, new ModernActionButton(2, "+ 1 min", ThemePalette.AccentEmerald), 0, 0);
            AddButtonToGrid(timeButtonsGrid, new ModernActionButton(3, "+ 5 min", ThemePalette.AccentEmerald), 0, 1);
            AddButtonToGrid(timeButtonsGrid, btnInterval10, 0, 2);

            AddButtonToGrid(timeButtonsGrid, new ModernActionButton(5, "− 1 min", ThemePalette.AccentRed), 1, 0);
            AddButtonToGrid(timeButtonsGrid, new ModernActionButton(6, "− 5 min", ThemePalette.AccentRed), 1, 1);
            AddButtonToGrid(timeButtonsGrid, btnInterval6, 1, 2);

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

            Grid personsTopRow = new Grid();
            TextBlock lblPersonsHeader = new TextBlock
            {
                Text = "ŠTEVILO OSEB",
                Foreground = ThemePalette.TextSecondary,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold
            };
            _txtSetCapacityValue = new TextBlock
            {
                Text = "Kapaciteta: --",
                Foreground = ThemePalette.TextSecondary,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold,
                HorizontalAlignment = HorizontalAlignment.Right,
                Cursor = Cursors.Hand,
                ToolTip = (_counterViewMode == 1)
                    ? "Kliknite za vklop / izklop prikaza kapacitete pod številom oseb"
                    : "Kliknite za vklop / izklop prikaza kapacitete ob številu oseb",
                Visibility = Visibility.Visible
            };
            _txtSetCapacityValue.MouseLeftButtonDown += (s, e) =>
            {
                ToggleCapacityUnder();
                e.Handled = true;
            };
            _txtSetCapacityValue.MouseEnter += (s, e) =>
            {
                _txtSetCapacityValue.Foreground = ThemePalette.TextPrimary;
            };
            _txtSetCapacityValue.MouseLeave += (s, e) =>
            {
                _txtSetCapacityValue.Foreground = ThemePalette.TextSecondary;
            };
            personsTopRow.Children.Add(lblPersonsHeader);
            personsTopRow.Children.Add(_txtSetCapacityValue);
            Grid.SetRow(personsTopRow, 0);

            Viewbox personsViewbox = new Viewbox
            {
                StretchDirection = StretchDirection.DownOnly,
                Stretch = Stretch.Uniform,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 1, 0, 2)
            };

            StackPanel personsVerticalStack = new StackPanel
            {
                Orientation = Orientation.Vertical,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };

            _personsNumberStack = new StackPanel
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
                FontWeight = FontWeights.SemiBold,
                TextAlignment = TextAlignment.Center
            };
            Typography.SetNumeralAlignment(_txtPersonsCurrent, FontNumeralAlignment.Tabular);

            _txtPersonsMax = new TextBlock
            {
                Text = "/--",
                Foreground = ThemePalette.TextMuted,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 46,
                FontWeight = FontWeights.Light,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0),
                Visibility = (_counterViewMode == 0 && _showCapacityUnder) ? Visibility.Visible : Visibility.Collapsed
            };
            Typography.SetNumeralAlignment(_txtPersonsMax, FontNumeralAlignment.Tabular);

            _txtUnderCapacityValue = new TextBlock
            {
                Text = "--",
                Foreground = ThemePalette.TextMuted,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 46,
                FontWeight = FontWeights.Normal,
                Opacity = 0.25,
                TextAlignment = TextAlignment.Center,
                HorizontalAlignment = HorizontalAlignment.Center,
                Visibility = (_counterViewMode == 1 && _showCapacityUnder) ? Visibility.Visible : Visibility.Collapsed
            };
            Typography.SetNumeralAlignment(_txtUnderCapacityValue, FontNumeralAlignment.Tabular);

            _personsNumberStack.Children.Add(_txtPersonsCurrent);
            _personsNumberStack.Children.Add(_txtPersonsMax);
            personsVerticalStack.Children.Add(_personsNumberStack);
            personsVerticalStack.Children.Add(_txtUnderCapacityValue);
            personsViewbox.Child = personsVerticalStack;
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

            personsDisplayGrid.Children.Add(personsTopRow);
            personsDisplayGrid.Children.Add(personsViewbox);
            personsDisplayGrid.Children.Add(_personsProgressTrack);
            personsDisplayBox.Child = personsDisplayGrid;
            Grid.SetRow(personsDisplayBox, 0);

            Grid personsButtonsGrid = new Grid();
            personsButtonsGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            personsButtonsGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            personsButtonsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            personsButtonsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            AddButtonToGrid(personsButtonsGrid, new ModernActionButton(7, "+ 1 oseba", ThemePalette.AccentEmerald, 14.5), 0, 0);
            AddButtonToGrid(personsButtonsGrid, new ModernActionButton(8, "− 1 oseba", ThemePalette.AccentRed, 14.5), 1, 0);

            ModernActionButton btnReset = new ModernActionButton(9, "Ponastavi\nštevec", ThemePalette.AccentBtnMuted, 14.0);
            btnReset.ToolTip = "Ponastavi število oseb na maksimalno kapaciteto (28)";
            btnReset.Clicked += async (btn) => await ExecuteCommandAsync(btn.CommandId);
            Grid.SetRow(btnReset, 0);
            Grid.SetRowSpan(btnReset, 2);
            Grid.SetColumn(btnReset, 1);
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
                else
                {
                    UpdateSectionRowHeights();
                }
            };

            _contentGrid.SizeChanged += (s, e) =>
            {
                UpdateSectionRowHeights();
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

            UpdateCounterViewVisuals();
            ApplyCounterScale(_currentScale);
            ApplyDimVisuals();
        }

        private void UpdateCounterViewVisuals()
        {
            bool isUnderMode = (_counterViewMode == 1);

            if (_bgIntervalGrid != null)
            {
                _bgIntervalGrid.Visibility = Visibility.Collapsed;
            }
            if (_txtTimerIntervalInline != null)
            {
                _txtTimerIntervalInline.Visibility = (!isUnderMode && _showIntervalBg) ? Visibility.Visible : Visibility.Collapsed;
            }
            if (_txtUnderIntervalValue != null)
            {
                _txtUnderIntervalValue.Visibility = (isUnderMode && _showIntervalBg) ? Visibility.Visible : Visibility.Collapsed;
            }
            if (_txtSetTimeValue != null)
            {
                _txtSetTimeValue.ToolTip = isUnderMode
                    ? "Kliknite za vklop / izklop prikaza intervala pod časom"
                    : "Kliknite za vklop / izklop prikaza intervala ob času";
            }
            if (_txtSetCapacityValue != null)
            {
                _txtSetCapacityValue.Visibility = Visibility.Visible;
                _txtSetCapacityValue.ToolTip = isUnderMode
                    ? "Kliknite za vklop / izklop prikaza kapacitete pod številom oseb"
                    : "Kliknite za vklop / izklop prikaza kapacitete ob številu oseb";
            }
            if (_txtPersonsMax != null)
            {
                _txtPersonsMax.Visibility = (!isUnderMode && _showCapacityUnder) ? Visibility.Visible : Visibility.Collapsed;
            }
            if (_txtUnderCapacityValue != null)
            {
                _txtUnderCapacityValue.Visibility = (isUnderMode && _showCapacityUnder) ? Visibility.Visible : Visibility.Collapsed;
            }

            ApplyCounterScale(_currentScale);
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
            double mainFontSize = Math.Max(26, Math.Round(46 * _currentScale));
            _txtTimerValue.FontSize = mainFontSize;
            if (_txtTimerIntervalInline != null)
                _txtTimerIntervalInline.FontSize = mainFontSize;
            if (_txtUnderIntervalValue != null)
                _txtUnderIntervalValue.FontSize = mainFontSize;
            _txtPersonsCurrent.FontSize = mainFontSize;
            if (_txtUnderCapacityValue != null)
                _txtUnderCapacityValue.FontSize = mainFontSize;
            _txtPersonsMax.FontSize = mainFontSize;

            if (_counterViewMode == 1)
            {
                double topOuter = -Math.Round(mainFontSize * 0.10);
                double innerGap = -Math.Round(mainFontSize * 0.15);
                double botOuter = -Math.Round(mainFontSize * 0.12);
                if (_timeNumberStack != null)
                    _timeNumberStack.Margin = new Thickness(0, topOuter, 0, _showIntervalBg ? innerGap : botOuter);
                if (_txtUnderIntervalValue != null)
                    _txtUnderIntervalValue.Margin = new Thickness(0, innerGap, 0, botOuter);
                if (_personsNumberStack != null)
                    _personsNumberStack.Margin = new Thickness(0, topOuter, 0, _showCapacityUnder ? innerGap : botOuter);
                if (_txtUnderCapacityValue != null)
                    _txtUnderCapacityValue.Margin = new Thickness(0, innerGap, 0, botOuter);
            }
            else
            {
                if (_timeNumberStack != null)
                    _timeNumberStack.Margin = new Thickness(0);
                if (_txtUnderIntervalValue != null)
                    _txtUnderIntervalValue.Margin = new Thickness(0);
                if (_personsNumberStack != null)
                    _personsNumberStack.Margin = new Thickness(0);
                if (_txtUnderCapacityValue != null)
                    _txtUnderCapacityValue.Margin = new Thickness(0);
            }

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

            UpdateSectionRowHeights();
        }

        private void UpdateSectionRowHeights()
        {
            if (_timeSection == null || _personsSection == null) return;
            if (_timeSection.RowDefinitions.Count < 2 || _personsSection.RowDefinitions.Count < 2) return;

            double contentH = _contentGrid != null ? _contentGrid.ActualHeight : 0.0;
            if (contentH <= 10.0)
            {
                _timeSection.RowDefinitions[0].Height = new GridLength(1.1, GridUnitType.Star);
                _timeSection.RowDefinitions[1].Height = new GridLength(1.0, GridUnitType.Star);
                _personsSection.RowDefinitions[0].Height = new GridLength(1.1, GridUnitType.Star);
                _personsSection.RowDefinitions[1].Height = new GridLength(1.0, GridUnitType.Star);
                return;
            }

            double sectionActualH;
            double sectionUnreducedH;
            double minButtonsH;

            if (_isNarrowLayout)
            {
                sectionActualH = Math.Max(0.0, (contentH - 10.0) * 0.5);
                sectionUnreducedH = sectionActualH + (_extraVerticalHeightReduction * 0.5);
                minButtonsH = 54.0;
            }
            else
            {
                sectionActualH = contentH;
                sectionUnreducedH = sectionActualH + _extraVerticalHeightReduction;
                minButtonsH = 62.0;
            }

            double displayRowH = Math.Round(sectionUnreducedH * (1.1 / 2.1));
            if (!_isNarrowLayout && displayRowH < 126.0)
            {
                displayRowH = 126.0;
            }

            if (sectionActualH - displayRowH < minButtonsH)
            {
                displayRowH = Math.Max(60.0, sectionActualH - minButtonsH);
            }

            _timeSection.RowDefinitions[0].Height = new GridLength(displayRowH, GridUnitType.Pixel);
            _timeSection.RowDefinitions[1].Height = new GridLength(1.0, GridUnitType.Star);
            _personsSection.RowDefinitions[0].Height = new GridLength(displayRowH, GridUnitType.Pixel);
            _personsSection.RowDefinitions[1].Height = new GridLength(1.0, GridUnitType.Star);
        }

        private void AddButtonToGrid(Grid grid, ModernActionButton btn, int row, int col)
        {
            btn.Clicked += async (b) => await ExecuteCommandAsync(b.CommandId);
            Grid.SetRow(btn, row);
            Grid.SetColumn(btn, col);
            grid.Children.Add(btn);
        }

        private async Task ExecuteCommandAsync(int commandId, bool isRemoteCommand = false)
        {
            await _commandLock.WaitAsync();
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
                            ApplyTelemetry(t, false, true);
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
                    ApplyTelemetry(t, true, isRemoteCommand);
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
                            ApplyTelemetry(t, true, true);
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
                    ApplyTelemetry(t, true, isRemoteCommand);
                }
                else
                {
                    StationTelemetry t = await Client.SendOperatorCommandAsync(commandId, 1800);
                    ApplyTelemetry(t, true, isRemoteCommand);
                }
            }
            catch
            {
                SetOfflineState();
            }
            finally
            {
                _isCommandBusy = false;
                _commandLock.Release();
            }
        }

        public async Task PollStatusAsync(bool forceSyncTime)
        {
            if (_isPolling || _isCommandBusy) return;
            _isPolling = true;
            try
            {
                StationTelemetry t = await Client.SendOperatorCommandAsync(0, 1800);
                ApplyTelemetry(t, forceSyncTime, false);
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

        private void ApplyTelemetry(StationTelemetry t, bool forceSyncTime, bool suppressCloudPublish = false)
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
            int prevMax = _maxPersons;

            bool timeJump = Math.Abs(t.RemainingTimeMs - _remainingMs) > 2500;

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
                if (clockReset && (t.CurrentPersons == 0 || t.CurrentPersons < prevPersons))
                {
                    if (DepartureDetected != null)
                    {
                        DepartureDetected();
                    }
                    if (!_isMuted)
                    {
                        int nextMinutes = (int)((t.SetTimeMs + 30000) / 60000);
                        if (nextMinutes <= 0) nextMinutes = 1;
                        ChimePlayer.SpeakDeparture(nextMinutes);
                    }
                }
            }

            RenderTime(isFirstLoad);
            RenderPersons(isFirstLoad);
            _hasInitialTelemetry = true;

            if (!suppressCloudPublish)
            {
                bool significantChange = isFirstLoad || wasOffline || forceSyncTime || timeJump ||
                                         prevPersons != _currentPersons || prevMax != _maxPersons || prevSetMs != _setMs;
                if (TelemetryUpdated != null)
                {
                    TelemetryUpdated(significantChange);
                }
            }
        }

        private void SetOfflineState()
        {
            bool wasOnline = _isOnline;
            _isOnline = false;
            _statusDot.Fill = ThemePalette.AccentCoral;
            _statusText.Text = "Ni povezave";
            _statusText.Foreground = ThemePalette.AccentCoral;

            _txtTimerValue.Text = "--:--";
            _txtTimerValue.Foreground = ThemePalette.TextMuted;
            if (_txtTimerIntervalInline != null)
                _txtTimerIntervalInline.Text = "/--";
            if (_txtBgIntervalValue != null)
                _txtBgIntervalValue.Text = "--:--";
            if (_txtUnderIntervalValue != null)
            {
                _txtUnderIntervalValue.Text = "--:--";
                _txtUnderIntervalValue.Foreground = ThemePalette.TextMuted;
            }
            _txtPersonsCurrent.Text = "--";
            _txtPersonsCurrent.Foreground = ThemePalette.TextMuted;
            _txtPersonsMax.Text = "/--";
            if (_txtSetCapacityValue != null)
                _txtSetCapacityValue.Text = "Kapaciteta: --";
            if (_txtUnderCapacityValue != null)
            {
                _txtUnderCapacityValue.Text = "--";
                _txtUnderCapacityValue.Foreground = ThemePalette.TextMuted;
            }
            _timerProgressFill.Width = 0;
            _personsProgressFill.Width = 0;

            if (StateTicked != null)
            {
                StateTicked();
            }

            if (wasOnline && TelemetryUpdated != null)
            {
                TelemetryUpdated(true);
            }
        }

        private void RenderTime(bool suppressChime = false)
        {
            if (!_isOnline) return;

            string formattedTime = FormatMinSec(_remainingMs);
            string formattedInterval = FormatMinSec(_setMs);
            string formattedIntervalInline = FormatIntervalInline(_setMs);
            _txtTimerValue.Text = formattedTime;
            _txtSetTimeValue.Text = "Interval: " + formattedInterval;
            if (_txtBgIntervalValue != null)
                _txtBgIntervalValue.Text = formattedInterval;
            if (_txtTimerIntervalInline != null)
                _txtTimerIntervalInline.Text = formattedIntervalInline;
            if (_txtUnderIntervalValue != null)
            {
                _txtUnderIntervalValue.Text = formattedInterval;
                _txtUnderIntervalValue.Foreground = ThemePalette.TextPrimary;
            }

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
            if (StateTicked != null)
            {
                StateTicked();
            }
        }

        private void RenderPersons(bool suppressChime = false)
        {
            if (!_isOnline) return;

            string formattedMax = _maxPersons.ToString("00", CultureInfo.InvariantCulture);
            _txtPersonsCurrent.Text = _currentPersons.ToString("00", CultureInfo.InvariantCulture);
            _txtPersonsMax.Text = "/" + formattedMax;
            if (_txtSetCapacityValue != null)
                _txtSetCapacityValue.Text = "Kapaciteta: " + formattedMax;
            if (_txtUnderCapacityValue != null)
            {
                _txtUnderCapacityValue.Text = formattedMax;
                _txtUnderCapacityValue.Foreground = ThemePalette.TextPrimary;
            }

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

        private static string FormatIntervalInline(long ms)
        {
            if (ms < 0) ms = 0;
            long totalSeconds = (ms + 499) / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            if (seconds > 0)
            {
                return string.Format(CultureInfo.InvariantCulture, "/{0:00}:{1:00}", minutes, seconds);
            }
            return string.Format(CultureInfo.InvariantCulture, "/{0:00}", minutes);
        }
    }

    /// <summary>
    /// Outbound HTTPS Cloud Relay Bridge that connects VzpenjacaKontrola (in the office network)
    /// with the Android mobile app from anywhere without requiring port-forwarding or admin rights.
    /// </summary>
    public class CloudRelayBridge : IDisposable
    {
        private readonly AppConfig _config;
        private readonly StationCardControl _cardZgoraj;
        private readonly StationCardControl _cardSpodaj;
        private readonly SemaphoreSlim _publishLock = new SemaphoreSlim(1, 1);
        private CancellationTokenSource _cts;
        private DateTime _lastPublishUtc = DateTime.MinValue;
        private DateTime _lastMobileActiveUtc = DateTime.MinValue;
        private string _lastStateSignature = "";
        private int _trailingPublishScheduled = 0;
        private readonly System.Collections.Generic.HashSet<string> _processedIds = new System.Collections.Generic.HashSet<string>();

        public bool IsConnected { get; private set; }
        public string LastStatusText { get; private set; }
        public event Action StatusChanged;

        static CloudRelayBridge()
        {
            try
            {
                // Enable TLS 1.2 (3072) and TLS 1.1 (768) on .NET Framework 4.x
                ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | (SecurityProtocolType)768 | SecurityProtocolType.Tls;
                ServicePointManager.DefaultConnectionLimit = 16;
                ServicePointManager.Expect100Continue = false;
            }
            catch { }
        }

        public CloudRelayBridge(AppConfig config, StationCardControl cardZgoraj, StationCardControl cardSpodaj)
        {
            _config = config;
            _cardZgoraj = cardZgoraj;
            _cardSpodaj = cardSpodaj;
            LastStatusText = "Pripravljeno";

            _cardZgoraj.TelemetryUpdated += OnStationTelemetryUpdated;
            _cardSpodaj.TelemetryUpdated += OnStationTelemetryUpdated;
        }

        public static string SanitizeChannel(string raw)
        {
            if (string.IsNullOrEmpty(raw)) return "vzpenjaca-3436";
            StringBuilder sb = new StringBuilder();
            foreach (char c in raw.Trim())
            {
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_')
                    sb.Append(char.ToLowerInvariant(c));
            }
            return sb.Length >= 4 ? sb.ToString() : "vzpenjaca-3436";
        }

        public static bool IsFirebaseServer(string server)
        {
            if (string.IsNullOrWhiteSpace(server)) return false;
            string s = server.Trim().ToLowerInvariant();
            return s.Contains("firebaseio.com") || s.Contains("firebasedatabase.app");
        }

        public static string NormalizeServerUrl(string raw)
        {
            if (string.IsNullOrWhiteSpace(raw)) return "https://ntfy.adminforge.de";
            string s = raw.Trim().TrimEnd('/');
            if (!s.StartsWith("http://", StringComparison.OrdinalIgnoreCase) &&
                !s.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                s = "https://" + s;
            }
            if (s.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
            {
                s = s.Substring(0, s.Length - 5).TrimEnd('/');
            }
            return s;
        }

        private static string GetFirebaseUrl(string server, string channel, string node)
        {
            string baseUrl = NormalizeServerUrl(server);
            return baseUrl + "/vzpenjaca/" + SanitizeChannel(channel) + "/" + node + ".json";
        }

        public string StateTopic
        {
            get { return SanitizeChannel(_config.CloudChannelId) + "-state"; }
        }

        public string CommandTopic
        {
            get { return SanitizeChannel(_config.CloudChannelId) + "-cmd"; }
        }

        public void Start()
        {
            Stop();
            if (!_config.CloudRelayEnabled)
            {
                IsConnected = false;
                UpdateStatus("Izklopljeno v nastavitvah");
                return;
            }

            _cts = new CancellationTokenSource();
            CancellationToken token = _cts.Token;
            Task.Run(() => ListenCommandsLoopAsync(token));
            Task.Run(async () =>
            {
                await Task.Delay(1500);
                if (!token.IsCancellationRequested)
                {
                    await PublishStateAsync(true);
                }
            });
        }

        public void Stop()
        {
            if (_cts != null)
            {
                try { _cts.Cancel(); } catch { }
                _cts = null;
            }
            IsConnected = false;
        }

        public void Restart()
        {
            _lastStateSignature = "";
            Start();
        }

        private void UpdateStatus(string text)
        {
            LastStatusText = text;
            if (StatusChanged != null)
            {
                try { StatusChanged(); } catch { }
            }
        }

        private void OnStationTelemetryUpdated(bool significantChange)
        {
            if (!_config.CloudRelayEnabled) return;

            DateTime now = DateTime.UtcNow;
            string server = NormalizeServerUrl(_config.CloudRelayServer);
            bool isFirebase = IsFirebaseServer(server);

            // For ntfy, only publish continuous turnstile updates when mobile client was recently active.
            // For Firebase Realtime Database, quota is 20,000+ writes/day and 1GB storage (overwriting 150B JSON),
            // so we can keep state fresh even before the mobile app sends a sync command, plus faster 1.2s updates when active!
            bool mobileRecentlyActive = (now - _lastMobileActiveUtc).TotalSeconds < 135.0;
            if (!isFirebase && !mobileRecentlyActive) return;

            double minIntervalMs = isFirebase ? (mobileRecentlyActive ? 1200.0 : 5000.0) : 3500.0;

            string sig = BuildStateSignature();
            bool sigChanged = (sig != _lastStateSignature);

            if (significantChange || sigChanged)
            {
                double elapsedMs = (now - _lastPublishUtc).TotalMilliseconds;
                if (elapsedMs >= minIntervalMs)
                {
                    Task.Run(() => PublishStateAsync(false));
                }
                else if (Interlocked.CompareExchange(ref _trailingPublishScheduled, 1, 0) == 0)
                {
                    int delayMs = Math.Max(300, (int)(minIntervalMs - elapsedMs) + 80);
                    Task.Run(async () =>
                    {
                        try
                        {
                            await Task.Delay(delayMs).ConfigureAwait(false);
                        }
                        finally
                        {
                            Interlocked.Exchange(ref _trailingPublishScheduled, 0);
                        }
                        if (isFirebase || (DateTime.UtcNow - _lastMobileActiveUtc).TotalSeconds < 135.0)
                        {
                            await PublishStateAsync(false).ConfigureAwait(false);
                        }
                    });
                }
            }
            else if ((now - _lastPublishUtc).TotalSeconds >= 45.0)
            {
                Task.Run(() => PublishStateAsync(false));
            }
        }

        private string BuildStateSignature()
        {
            return string.Format(
                CultureInfo.InvariantCulture,
                "{0}:{1}:{2}:{3}|{4}:{5}:{6}:{7}",
                _cardZgoraj.IsOnline ? 1 : 0,
                _cardZgoraj.CurrentPersons,
                _cardZgoraj.MaxPersons,
                _cardZgoraj.SetMs,
                _cardSpodaj.IsOnline ? 1 : 0,
                _cardSpodaj.CurrentPersons,
                _cardSpodaj.MaxPersons,
                _cardSpodaj.SetMs);
        }

        public async Task<bool> PublishStateAsync(bool force)
        {
            if (!_config.CloudRelayEnabled) return false;

            string server = NormalizeServerUrl(_config.CloudRelayServer);
            bool isFirebase = IsFirebaseServer(server);
            double minThrottleMs = isFirebase ? 1000.0 : 3200.0;

            DateTime now = DateTime.UtcNow;
            if (!force && (now - _lastPublishUtc).TotalMilliseconds < minThrottleMs)
                return false;

            int lockTimeoutMs = force ? 5000 : 0;
            if (!await _publishLock.WaitAsync(lockTimeoutMs).ConfigureAwait(false))
                return false;

            try
            {
                _lastPublishUtc = DateTime.UtcNow;
                _lastStateSignature = BuildStateSignature();

                long unixMs = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalMilliseconds;
                string payload = string.Format(
                    CultureInfo.InvariantCulture,
                    "{{\"ts\":{0},\"zOn\":{1},\"zCur\":{2},\"zMax\":{3},\"zRem\":{4},\"zSet\":{5},\"sOn\":{6},\"sCur\":{7},\"sMax\":{8},\"sRem\":{9},\"sSet\":{10}}}",
                    unixMs,
                    _cardZgoraj.IsOnline ? 1 : 0,
                    _cardZgoraj.CurrentPersons,
                    _cardZgoraj.MaxPersons,
                    _cardZgoraj.RemainingMs,
                    _cardZgoraj.SetMs,
                    _cardSpodaj.IsOnline ? 1 : 0,
                    _cardSpodaj.CurrentPersons,
                    _cardSpodaj.MaxPersons,
                    _cardSpodaj.RemainingMs,
                    _cardSpodaj.SetMs);

                string url = isFirebase
                    ? GetFirebaseUrl(server, _config.CloudChannelId, "state")
                    : (server + "/" + StateTopic);

                HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
                req.Method = isFirebase ? "PUT" : "POST";
                req.ContentType = isFirebase ? "application/json; charset=utf-8" : "text/plain; charset=utf-8";
                req.Timeout = 6000;
                req.ReadWriteTimeout = 6000;
                req.KeepAlive = true;

                byte[] bytes = Encoding.UTF8.GetBytes(payload);
                req.ContentLength = bytes.Length;

                using (Stream reqStream = await req.GetRequestStreamAsync().ConfigureAwait(false))
                {
                    await reqStream.WriteAsync(bytes, 0, bytes.Length).ConfigureAwait(false);
                }

                using (WebResponse resp = await req.GetResponseAsync().ConfigureAwait(false))
                {
                    return true;
                }
            }
            catch (WebException wex)
            {
                HttpWebResponse httpResp = wex.Response as HttpWebResponse;
                if (httpResp != null)
                {
                    int code = (int)httpResp.StatusCode;
                    if (code == 429)
                    {
                        UpdateStatus("Omejitev strežnika (429) — uporabite Firebase URL");
                    }
                    else if (code == 401 || code == 403)
                    {
                        UpdateStatus("Firebase dostop zavrnjen (" + code + ") — preverite Rules");
                    }
                }
                return false;
            }
            catch
            {
                return false;
            }
            finally
            {
                _publishLock.Release();
            }
        }

        private async Task ListenCommandsLoopAsync(CancellationToken token)
        {
            bool hasConnectedOnce = false;
            while (!token.IsCancellationRequested)
            {
                HttpWebRequest req = null;
                bool failed = false;
                try
                {
                    string server = NormalizeServerUrl(_config.CloudRelayServer);
                    bool isFirebase = IsFirebaseServer(server);
                    string streamUrl = isFirebase
                        ? GetFirebaseUrl(server, _config.CloudChannelId, "cmd")
                        : (server + "/" + CommandTopic + (hasConnectedOnce ? "/json?since=15s" : "/json"));

                    UpdateStatus(isFirebase ? "Povezovanje v Firebase..." : "Povezovanje v oblak...");
                    req = (HttpWebRequest)WebRequest.Create(streamUrl);
                    req.Method = "GET";
                    req.Accept = isFirebase ? "text/event-stream" : "application/x-ndjson, application/json";
                    req.Timeout = 15000;
                    req.ReadWriteTimeout = 75000;
                    req.AllowReadStreamBuffering = false;

                    using (token.Register(() => { try { req.Abort(); } catch { } }))
                    using (WebResponse resp = await req.GetResponseAsync().ConfigureAwait(false))
                    using (Stream stream = resp.GetResponseStream())
                    using (StreamReader reader = new StreamReader(stream, Encoding.UTF8))
                    {
                        hasConnectedOnce = true;
                        IsConnected = true;
                        string providerLabel = isFirebase ? "Firebase" : "Oblak";
                        UpdateStatus(providerLabel + " povezano (" + SanitizeChannel(_config.CloudChannelId) + ")");

                        string currentSseEvent = "";
                        while (!token.IsCancellationRequested)
                        {
                            Task<string> readTask = reader.ReadLineAsync();
                            Task completed = await Task.WhenAny(readTask, Task.Delay(75000, token)).ConfigureAwait(false);
                            if (completed != readTask)
                            {
                                try { req.Abort(); } catch { }
                                break;
                            }

                            string line = await readTask.ConfigureAwait(false);
                            if (line == null) break;
                            string trimmed = line.Trim();
                            if (trimmed.Length == 0) continue;

                            if (isFirebase)
                            {
                                if (trimmed.StartsWith("event:", StringComparison.OrdinalIgnoreCase))
                                {
                                    currentSseEvent = trimmed.Substring(6).Trim();
                                }
                                else if (trimmed.StartsWith("data:", StringComparison.OrdinalIgnoreCase))
                                {
                                    string dataJson = trimmed.Substring(5).Trim();
                                    if ((string.Equals(currentSseEvent, "put", StringComparison.OrdinalIgnoreCase) ||
                                         string.Equals(currentSseEvent, "patch", StringComparison.OrdinalIgnoreCase)) &&
                                        !string.IsNullOrEmpty(dataJson) && dataJson != "null")
                                    {
                                        await HandleFirebaseCommandSseDataAsync(server, dataJson).ConfigureAwait(false);
                                    }
                                }
                            }
                            else
                            {
                                await HandleIncomingStreamLineAsync(trimmed).ConfigureAwait(false);
                            }
                        }
                    }
                }
                catch (WebException wex)
                {
                    IsConnected = false;
                    failed = true;
                    HttpWebResponse httpResp = wex.Response as HttpWebResponse;
                    if (httpResp != null && ((int)httpResp.StatusCode == 401 || (int)httpResp.StatusCode == 403))
                    {
                        UpdateStatus("Firebase dostop zavrnjen — omogočite .read/.write v Rules");
                    }
                }
                catch
                {
                    IsConnected = false;
                    failed = true;
                }

                if (failed && !token.IsCancellationRequested)
                {
                    UpdateStatus("Ponovno povezovanje...");
                    await Task.Delay(2500).ConfigureAwait(false);
                }
            }
        }

        private async Task ClearFirebaseCommandNodeAsync(string server)
        {
            try
            {
                string url = GetFirebaseUrl(server, _config.CloudChannelId, "cmd");
                HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
                req.Method = "DELETE";
                req.Timeout = 5000;
                using (WebResponse resp = await req.GetResponseAsync().ConfigureAwait(false)) { }
            }
            catch { }
        }

        private async Task HandleFirebaseCommandSseDataAsync(string server, string dataJson)
        {
            try
            {
                // Firebase SSE sends {"path":"/","data":{"station":"zgoraj","cmd":7,"pin":"","ts":1700000000,"id":"..."}}
                // Ignore if data is null (which happens right after we DELETE the processed command)
                if (dataJson.IndexOf("\"data\":null", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    dataJson.IndexOf("\"data\": null", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    return;
                }

                string station = ExtractJsonString(dataJson, "station");
                if (string.IsNullOrEmpty(station)) return;

                string pin = ExtractJsonString(dataJson, "pin");
                int cmdId = (int)ExtractJsonLong(dataJson, "cmd", -1);
                long cmdTs = ExtractJsonLong(dataJson, "ts", 0);
                string msgId = ExtractJsonString(dataJson, "id");
                if (string.IsNullOrEmpty(msgId) && cmdTs > 0)
                {
                    msgId = station + ":" + cmdId.ToString(CultureInfo.InvariantCulture) + ":" + cmdTs.ToString(CultureInfo.InvariantCulture);
                }

                // Clear the command from Firebase immediately so it is never re-executed on reconnect
                Task.Run(() => ClearFirebaseCommandNodeAsync(server));

                // Ignore stale commands older than 30 seconds
                if (cmdTs > 0)
                {
                    long nowMs = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalMilliseconds;
                    if (nowMs - cmdTs > 30000L)
                    {
                        return;
                    }
                }

                if (!string.IsNullOrEmpty(msgId))
                {
                    lock (_processedIds)
                    {
                        if (_processedIds.Contains(msgId)) return;
                        if (_processedIds.Count > 200) _processedIds.Clear();
                        _processedIds.Add(msgId);
                    }
                }

                await ExecuteParsedMobileCommandAsync(station, pin, cmdId).ConfigureAwait(false);
            }
            catch { }
        }

        private async Task HandleIncomingStreamLineAsync(string line)
        {
            try
            {
                string evt = ExtractJsonString(line, "event");
                if (!string.Equals(evt, "message", StringComparison.OrdinalIgnoreCase))
                    return;

                string msgId = ExtractJsonString(line, "id");
                if (!string.IsNullOrEmpty(msgId))
                {
                    lock (_processedIds)
                    {
                        if (_processedIds.Contains(msgId)) return;
                        if (_processedIds.Count > 200) _processedIds.Clear();
                        _processedIds.Add(msgId);
                    }
                }

                string innerJson = ExtractJsonString(line, "message");
                if (string.IsNullOrEmpty(innerJson)) return;

                string station = ExtractJsonString(innerJson, "station");
                string pin = ExtractJsonString(innerJson, "pin");
                int cmdId = (int)ExtractJsonLong(innerJson, "cmd", -1);

                await ExecuteParsedMobileCommandAsync(station, pin, cmdId).ConfigureAwait(false);
            }
            catch { }
        }

        private async Task ExecuteParsedMobileCommandAsync(string station, string pin, int cmdId)
        {
            if (!string.IsNullOrEmpty(_config.CloudPin))
            {
                if (!string.Equals(pin ?? "", _config.CloudPin.Trim(), StringComparison.Ordinal))
                {
                    return;
                }
            }

            _lastMobileActiveUtc = DateTime.UtcNow;

            if (string.Equals(station, "sync", StringComparison.OrdinalIgnoreCase) || cmdId == 0)
            {
                await PublishStateAsync(true).ConfigureAwait(false);
                return;
            }

            if (cmdId >= 1 && cmdId <= 11)
            {
                if (string.Equals(station, "zgoraj", StringComparison.OrdinalIgnoreCase))
                {
                    UpdateStatus("Mobilni ukaz: Zgoraj ?" + cmdId);
                    await _cardZgoraj.ExecuteRemoteCommandAsync(cmdId).ConfigureAwait(false);
                    await PublishStateAsync(true).ConfigureAwait(false);
                }
                else if (string.Equals(station, "spodaj", StringComparison.OrdinalIgnoreCase))
                {
                    UpdateStatus("Mobilni ukaz: Spodaj ?" + cmdId);
                    await _cardSpodaj.ExecuteRemoteCommandAsync(cmdId).ConfigureAwait(false);
                    await PublishStateAsync(true).ConfigureAwait(false);
                }
            }
        }

        private static int FindJsonKeyColon(string json, string key)
        {
            if (string.IsNullOrEmpty(json) || string.IsNullOrEmpty(key)) return -1;
            string pattern = "\"" + key + "\"";
            int searchFrom = 0;
            while (searchFrom < json.Length)
            {
                int idx = json.IndexOf(pattern, searchFrom, StringComparison.OrdinalIgnoreCase);
                if (idx < 0) return -1;

                int p = idx - 1;
                while (p >= 0 && (json[p] == ' ' || json[p] == '\t' || json[p] == '\r' || json[p] == '\n')) p--;
                bool validPrev = (p >= 0 && (json[p] == '{' || json[p] == ','));

                int c = idx + pattern.Length;
                while (c < json.Length && (json[c] == ' ' || json[c] == '\t' || json[c] == '\r' || json[c] == '\n')) c++;
                bool validColon = (c < json.Length && json[c] == ':');

                if (validPrev && validColon)
                    return c;

                searchFrom = idx + pattern.Length;
            }
            return -1;
        }

        public static string ExtractJsonString(string json, string key)
        {
            int colon = FindJsonKeyColon(json, key);
            if (colon < 0) return null;
            int qStart = json.IndexOf('"', colon + 1);
            if (qStart < 0) return null;

            StringBuilder sb = new StringBuilder();
            bool escaped = false;
            for (int i = qStart + 1; i < json.Length; i++)
            {
                char c = json[i];
                if (escaped)
                {
                    if (c == '"' || c == '\\' || c == '/') sb.Append(c);
                    else if (c == 'n') sb.Append('\n');
                    else if (c == 'r') sb.Append('\r');
                    else if (c == 't') sb.Append('\t');
                    else sb.Append(c);
                    escaped = false;
                }
                else
                {
                    if (c == '\\') escaped = true;
                    else if (c == '"') return sb.ToString();
                    else sb.Append(c);
                }
            }
            return sb.ToString();
        }

        public static long ExtractJsonLong(string json, string key, long defaultValue)
        {
            int colon = FindJsonKeyColon(json, key);
            if (colon < 0) return defaultValue;

            int i = colon + 1;
            while (i < json.Length && (json[i] == ' ' || json[i] == '\t' || json[i] == '"')) i++;
            int start = i;
            if (i < json.Length && json[i] == '-') i++;
            while (i < json.Length && char.IsDigit(json[i])) i++;
            if (i <= start) return defaultValue;

            long val;
            if (long.TryParse(json.Substring(start, i - start), NumberStyles.Integer, CultureInfo.InvariantCulture, out val))
                return val;
            return defaultValue;
        }

        public void Dispose()
        {
            Stop();
        }
    }

    public class CloudSettingsDialog : Window
    {
        private readonly AppConfig _config;
        private readonly CloudRelayBridge _bridge;
        private readonly CheckBox _chkEnabled;
        private readonly TextBox _txtChannel;
        private readonly TextBox _txtPin;
        private readonly TextBox _txtServer;
        private readonly TextBlock _lblLiveStatus;

        public CloudSettingsDialog(Window owner, AppConfig config, CloudRelayBridge bridge)
        {
            Owner = owner;
            _config = config;
            _bridge = bridge;

            Title = "Mobilni dostop (Firebase / Oblak) — Vzpenjača";
            Width = 480;
            Height = 425;
            ResizeMode = ResizeMode.NoResize;
            WindowStartupLocation = WindowStartupLocation.CenterOwner;
            Background = ThemePalette.WindowBg;
            Foreground = ThemePalette.TextPrimary;

            StackPanel root = new StackPanel { Margin = new Thickness(18, 16, 18, 16) };

            TextBlock title = new TextBlock
            {
                Text = "Povezava z mobilno aplikacijo (Android)",
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 15,
                FontWeight = FontWeights.SemiBold,
                Foreground = ThemePalette.TextPrimary,
                Margin = new Thickness(0, 0, 0, 6)
            };
            root.Children.Add(title);

            _lblLiveStatus = new TextBlock
            {
                Text = "Status: " + (_bridge != null ? _bridge.LastStatusText : ""),
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 12,
                Foreground = ThemePalette.AccentEmerald,
                Margin = new Thickness(0, 0, 0, 12)
            };
            root.Children.Add(_lblLiveStatus);

            _chkEnabled = new CheckBox
            {
                Content = "Omogoči oblačni most za upravljanje prek telefona",
                IsChecked = _config.CloudRelayEnabled,
                Foreground = ThemePalette.TextPrimary,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 12.5,
                Margin = new Thickness(0, 0, 0, 14)
            };
            root.Children.Add(_chkEnabled);

            root.Children.Add(CreateLabel("Firebase Realtime Database URL (ali ntfy strežnik):"));
            _txtServer = CreateTextBox(_config.CloudRelayServer);
            _txtServer.ToolTip = "Primer za Google Firebase: https://ime-projekta-default-rtdb.europe-west1.firebasedatabase.app";
            root.Children.Add(_txtServer);

            TextBlock hintFirebase = new TextBlock
            {
                Text = "Priporočeno: Vpišite vaš Google Firebase Realtime Database URL (.firebasedatabase.app ali .firebaseio.com) za neomejeno delovanje brez dnevnih omejitev.",
                Foreground = ThemePalette.TextMuted,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 11,
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, -6, 0, 10)
            };
            root.Children.Add(hintFirebase);

            root.Children.Add(CreateLabel("Koda kanala (vpišite isto kodo v Android aplikaciji):"));
            _txtChannel = CreateTextBox(_config.CloudChannelId);
            root.Children.Add(_txtChannel);

            root.Children.Add(CreateLabel("Varnostni PIN za ukaze (prazno = brez PIN-a):"));
            _txtPin = CreateTextBox(_config.CloudPin);
            root.Children.Add(_txtPin);

            StackPanel btnRow = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right,
                Margin = new Thickness(0, 10, 0, 0)
            };

            Button btnSyncNow = CreateDialogButton("Pošlji stanje zdaj", false);
            btnSyncNow.Margin = new Thickness(0, 0, 8, 0);
            btnSyncNow.Click += async (s, e) =>
            {
                if (_bridge != null)
                {
                    _config.CloudRelayServer = CloudRelayBridge.NormalizeServerUrl(_txtServer.Text);
                    _config.CloudChannelId = CloudRelayBridge.SanitizeChannel(_txtChannel.Text);
                    bool ok = await _bridge.PublishStateAsync(true);
                    _lblLiveStatus.Text = ok
                        ? "Status: Stanje uspešno poslano v oblak"
                        : "Status: Napaka pri pošiljanju (preverite URL / Rules)";
                }
            };

            Button btnSave = CreateDialogButton("Shrani", true);
            btnSave.Click += (s, e) =>
            {
                _config.CloudRelayEnabled = _chkEnabled.IsChecked == true;
                _config.CloudChannelId = CloudRelayBridge.SanitizeChannel(_txtChannel.Text);
                _config.CloudPin = (_txtPin.Text ?? "").Trim();
                _config.CloudRelayServer = CloudRelayBridge.NormalizeServerUrl(_txtServer.Text);
                _config.Save();
                if (_bridge != null) _bridge.Restart();
                DialogResult = true;
                Close();
            };

            btnRow.Children.Add(btnSyncNow);
            btnRow.Children.Add(btnSave);
            root.Children.Add(btnRow);

            Content = root;
        }

        private static TextBlock CreateLabel(string text)
        {
            return new TextBlock
            {
                Text = text,
                Foreground = ThemePalette.TextSecondary,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 11.5,
                Margin = new Thickness(0, 0, 0, 4)
            };
        }

        private static TextBox CreateTextBox(string initial)
        {
            return new TextBox
            {
                Text = initial ?? "",
                Background = ThemePalette.CardBg,
                Foreground = ThemePalette.TextPrimary,
                BorderBrush = ThemePalette.BtnBorder,
                CaretBrush = ThemePalette.TextPrimary,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 13,
                Padding = new Thickness(8, 5, 8, 5),
                Margin = new Thickness(0, 0, 0, 10)
            };
        }

        private static Button CreateDialogButton(string text, bool primary)
        {
            return new Button
            {
                Content = text,
                Padding = new Thickness(14, 6, 14, 6),
                Background = primary ? ThemePalette.AccentBlueActiveBg : ThemePalette.BtnBg,
                Foreground = ThemePalette.TextPrimary,
                BorderBrush = primary ? ThemePalette.AccentBlue : ThemePalette.BtnBorder,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 12.5,
                Cursor = Cursors.Hand
            };
        }
    }

    public class VoiceSettingsDialog : Window
    {
        private readonly AppConfig _config;
        private readonly CheckBox _chkEnabled;
        private readonly TextBox _txtTemplate;
        private readonly Slider _sliderGain;
        private readonly TextBlock _lblGainValue;
        private readonly TextBlock _lblPreview;

        public VoiceSettingsDialog(Window owner, AppConfig config)
        {
            Owner = owner;
            _config = config;

            Title = "Glasovno obvestilo ob odhodu — Vzpenjača";
            Width = 480;
            Height = 395;
            ResizeMode = ResizeMode.NoResize;
            WindowStartupLocation = WindowStartupLocation.CenterOwner;
            Background = ThemePalette.WindowBg;
            Foreground = ThemePalette.TextPrimary;

            StackPanel root = new StackPanel { Margin = new Thickness(18, 16, 18, 16) };

            TextBlock title = new TextBlock
            {
                Text = "Glasovno obvestilo ob odhodu",
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 15,
                FontWeight = FontWeights.SemiBold,
                Foreground = ThemePalette.TextPrimary,
                Margin = new Thickness(0, 0, 0, 12)
            };
            root.Children.Add(title);

            _chkEnabled = new CheckBox
            {
                Content = "Omogoči glasovno obvestilo ob odhodu vzpenjače",
                IsChecked = _config.VoiceAnnouncementEnabled,
                Foreground = ThemePalette.TextPrimary,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 12.5,
                Margin = new Thickness(0, 0, 0, 14)
            };
            root.Children.Add(_chkEnabled);

            Grid gainHeader = new Grid { Margin = new Thickness(0, 0, 0, 4) };
            gainHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            gainHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            TextBlock lblGainTitle = CreateLabel("Glasnost obvestila (ojačanje):");
            lblGainTitle.Margin = new Thickness(0);
            Grid.SetColumn(lblGainTitle, 0);
            gainHeader.Children.Add(lblGainTitle);

            _lblGainValue = new TextBlock
            {
                Text = Math.Round(_config.VoiceAnnouncementGain * 100.0).ToString("0", CultureInfo.InvariantCulture) + " %",
                Foreground = ThemePalette.AccentEmerald,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 12
            };
            Grid.SetColumn(_lblGainValue, 1);
            gainHeader.Children.Add(_lblGainValue);
            root.Children.Add(gainHeader);

            _sliderGain = new Slider
            {
                Minimum = 1.0,
                Maximum = 4.0,
                Value = Math.Max(1.0, Math.Min(4.0, _config.VoiceAnnouncementGain)),
                TickFrequency = 0.25,
                IsSnapToTickEnabled = true,
                Margin = new Thickness(0, 0, 0, 14),
                Cursor = Cursors.Hand
            };
            _sliderGain.ValueChanged += (s, e) =>
            {
                if (_lblGainValue != null)
                {
                    _lblGainValue.Text = Math.Round(_sliderGain.Value * 100.0).ToString("0", CultureInfo.InvariantCulture) + " %";
                }
            };
            root.Children.Add(_sliderGain);

            root.Children.Add(CreateLabel("Besedilo obvestila (uporabite {minute} za interval naslednje vožnje):"));

            _txtTemplate = new TextBox
            {
                Text = string.IsNullOrWhiteSpace(_config.VoiceAnnouncementTemplate)
                    ? AppConfig.DefaultVoiceTemplate
                    : _config.VoiceAnnouncementTemplate,
                Background = ThemePalette.CardBg,
                Foreground = ThemePalette.TextPrimary,
                BorderBrush = ThemePalette.BtnBorder,
                CaretBrush = ThemePalette.TextPrimary,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 13,
                Padding = new Thickness(8, 6, 8, 6),
                TextWrapping = TextWrapping.Wrap,
                AcceptsReturn = false,
                Height = 56,
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                Margin = new Thickness(0, 0, 0, 6)
            };
            root.Children.Add(_txtTemplate);

            _lblPreview = new TextBlock
            {
                Text = "Primer izgovora: \"" + ChimePlayer.BuildAnnouncementText(_txtTemplate.Text, 10) + "\"",
                Foreground = ThemePalette.TextMuted,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 11.2,
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 14)
            };
            _txtTemplate.TextChanged += (s, e) =>
            {
                if (_lblPreview != null)
                {
                    _lblPreview.Text = "Primer izgovora: \"" + ChimePlayer.BuildAnnouncementText(_txtTemplate.Text, 10) + "\"";
                }
            };
            root.Children.Add(_lblPreview);

            Grid btnRow = new Grid { Margin = new Thickness(0, 6, 0, 0) };
            btnRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            btnRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            btnRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            btnRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            Button btnDefault = CreateDialogButton("Privzeto besedilo", false);
            btnDefault.Click += (s, e) =>
            {
                _txtTemplate.Text = AppConfig.DefaultVoiceTemplate;
            };
            Grid.SetColumn(btnDefault, 0);
            btnRow.Children.Add(btnDefault);

            Button btnTest = CreateDialogButton("Preizkusi glas", false);
            btnTest.Margin = new Thickness(0, 0, 8, 0);
            btnTest.Click += (s, e) =>
            {
                string sampleText = ChimePlayer.BuildAnnouncementText(_txtTemplate.Text, 10);
                ChimePlayer.SpeakCustomText(sampleText, _sliderGain.Value);
            };
            Grid.SetColumn(btnTest, 2);
            btnRow.Children.Add(btnTest);

            Button btnSave = CreateDialogButton("Shrani", true);
            btnSave.Click += (s, e) =>
            {
                _config.VoiceAnnouncementEnabled = _chkEnabled.IsChecked == true;
                string tpl = (_txtTemplate.Text ?? "").Replace("\r", " ").Replace("\n", " ").Trim();
                _config.VoiceAnnouncementTemplate = string.IsNullOrEmpty(tpl)
                    ? AppConfig.DefaultVoiceTemplate
                    : tpl;
                _config.VoiceAnnouncementGain = Math.Round(_sliderGain.Value, 2);
                _config.Save();

                ChimePlayer.VoiceEnabled = _config.VoiceAnnouncementEnabled;
                ChimePlayer.VoiceTemplate = _config.VoiceAnnouncementTemplate;
                ChimePlayer.VoiceGain = _config.VoiceAnnouncementGain;

                DialogResult = true;
                Close();
            };
            Grid.SetColumn(btnSave, 3);
            btnRow.Children.Add(btnSave);

            root.Children.Add(btnRow);

            Content = root;
        }

        private static TextBlock CreateLabel(string text)
        {
            return new TextBlock
            {
                Text = text,
                Foreground = ThemePalette.TextSecondary,
                FontFamily = new FontFamily("Segoe UI"),
                FontSize = 11.5,
                Margin = new Thickness(0, 0, 0, 4)
            };
        }

        private static Button CreateDialogButton(string text, bool primary)
        {
            return new Button
            {
                Content = text,
                Padding = new Thickness(12, 6, 12, 6),
                Background = primary ? ThemePalette.AccentBlueActiveBg : ThemePalette.BtnBg,
                Foreground = ThemePalette.TextPrimary,
                BorderBrush = primary ? ThemePalette.AccentBlue : ThemePalette.BtnBorder,
                FontFamily = new FontFamily("Segoe UI Semibold, Segoe UI"),
                FontSize = 12.5,
                Cursor = Cursors.Hand
            };
        }
    }

    public class MainWindow : Window
    {
        private const int WM_SYSCOMMAND = 0x0112;
        private const int WM_QUERYENDSESSION = 0x0011;
        private const int WM_ENDSESSION = 0x0016;
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
        private const int IDM_CLOUD_SETTINGS = 0x1005;
        private const int IDM_ZOOM_IN = 0x1010;
        private const int IDM_ZOOM_OUT = 0x1011;
        private const int IDM_ZOOM_RESET = 0x1012;
        private const int IDM_TOGGLE_CONTROLS = 0x1013;
        private const int IDM_VIEW_CLASSIC = 0x1014;
        private const int IDM_VIEW_UNDER = 0x1015;
        private const int IDM_SHOW_FUNICULAR_TRACK = 0x1016;
        private const int IDM_FUNICULAR_DIRECTION = 0x1017;
        private const int IDM_EXIT = 0x1019;
        private const int IDM_VERSION = 0x1020;

        private const double HiddenMiddleGapHeight = 10.0;
        private const double VisibleFunicularRowHeight = 56.0;

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

        [DllImport("user32.dll")]
        private static extern bool SetForegroundWindow(IntPtr hWnd);

        private readonly AppConfig _config;
        private readonly StationCardControl _cardZgoraj;
        private readonly StationCardControl _cardSpodaj;
        private readonly RowDefinition _rowFunicularTrack;
        private readonly FunicularTrackControl _funicularTrack;
        private readonly CloudRelayBridge _cloudBridge;
        private IntPtr _hSysMenu = IntPtr.Zero;
        private bool _isInitialized = false;
        private Forms.NotifyIcon _trayIcon;
        private bool _isExplicitExit = false;
        private bool _hasShownTrayBalloon = false;
        private string _lastDepartedStationId = "zgoraj";
        private double _lastKnownSingleCarPos = 0.0;

        public MainWindow()
        {
            _config = AppConfig.Load();
            AppConfig.ApplyAutostart(_config.StartWithWindows);
            ChimePlayer.Enabled = _config.SoundEnabled;
            ChimePlayer.VoiceEnabled = _config.VoiceAnnouncementEnabled;
            ChimePlayer.VoiceTemplate = _config.VoiceAnnouncementTemplate;
            ChimePlayer.VoiceGain = _config.VoiceAnnouncementGain;

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
            _rowFunicularTrack = new RowDefinition
            {
                Height = new GridLength(_config.ShowFunicularTrack ? VisibleFunicularRowHeight : HiddenMiddleGapHeight, GridUnitType.Pixel)
            };
            mainGrid.RowDefinitions.Add(_rowFunicularTrack);
            mainGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

            _cardZgoraj = new StationCardControl("ZGORNJA POSTAJA", _config.IpZgoraj, _config.CounterScale, _config.MuteZgoraj, _config.DimZgoraj, _config.ShowIntervalBgZgoraj, _config.ShowCapacityZgoraj, _config.CounterViewMode);
            _cardZgoraj.QuickSettingsChanged += () =>
            {
                _config.MuteZgoraj = _cardZgoraj.IsMuted;
                _config.DimZgoraj = _cardZgoraj.IsDimmed;
                _config.ShowIntervalBgZgoraj = _cardZgoraj.ShowIntervalBg;
                _config.ShowCapacityZgoraj = _cardZgoraj.ShowCapacityUnder;
                _config.Save();
            };
            _cardZgoraj.DepartureDetected += () =>
            {
                _lastDepartedStationId = "zgoraj";
                SyncFunicularPosition();
            };
            _cardZgoraj.StateTicked += SyncFunicularPosition;
            Grid.SetRow(_cardZgoraj, 0);

            _funicularTrack = new FunicularTrackControl(_config.FunicularTrackReversed)
            {
                Margin = new Thickness(0, 6, 0, 6)
            };
            _funicularTrack.DirectionToggleRequested += () =>
            {
                ToggleFunicularDirection(true);
            };
            _funicularTrack.DirectionToggled += () =>
            {
                _config.FunicularTrackReversed = _funicularTrack.IsZgornjaToSpodnja;
                _config.Save();
                SyncFunicularPosition();
                RefreshSystemMenu();
            };
            Grid.SetRow(_funicularTrack, 1);

            _cardSpodaj = new StationCardControl("SPODNJA POSTAJA", _config.IpSpodaj, _config.CounterScale, _config.MuteSpodaj, _config.DimSpodaj, _config.ShowIntervalBgSpodaj, _config.ShowCapacitySpodaj, _config.CounterViewMode);
            _cardSpodaj.QuickSettingsChanged += () =>
            {
                _config.MuteSpodaj = _cardSpodaj.IsMuted;
                _config.DimSpodaj = _cardSpodaj.IsDimmed;
                _config.ShowIntervalBgSpodaj = _cardSpodaj.ShowIntervalBg;
                _config.ShowCapacitySpodaj = _cardSpodaj.ShowCapacityUnder;
                _config.Save();
            };
            _cardSpodaj.DepartureDetected += () =>
            {
                _lastDepartedStationId = "spodaj";
                SyncFunicularPosition();
            };
            _cardSpodaj.StateTicked += SyncFunicularPosition;
            Grid.SetRow(_cardSpodaj, 2);

            mainGrid.Children.Add(_cardZgoraj);
            mainGrid.Children.Add(_funicularTrack);
            mainGrid.Children.Add(_cardSpodaj);

            ApplyFunicularTrackVisibility();
            SyncFunicularPosition();

            _cloudBridge = new CloudRelayBridge(_config, _cardZgoraj, _cardSpodaj);

            Content = mainGrid;

            SourceInitialized += OnSourceInitialized;

            LocationChanged += (s, e) => SaveWindowBounds();
            SizeChanged += (s, e) => SaveWindowBounds();
            InitializeTrayIcon();

            Closing += (s, e) =>
            {
                if (!_isExplicitExit)
                {
                    e.Cancel = true;
                    SaveWindowBounds();
                    Hide();
                    ShowTrayNotificationOnce();
                    return;
                }

                SaveWindowBounds();
                if (_cloudBridge != null) _cloudBridge.Dispose();
                DisposeTrayIcon();
            };

            if (Application.Current != null)
            {
                Application.Current.SessionEnding += (s, e) =>
                {
                    _isExplicitExit = true;
                };
            }

            AppDomain.CurrentDomain.ProcessExit += (s, e) =>
            {
                DisposeTrayIcon();
            };

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
                _cloudBridge.Start();
                await Task.WhenAll(
                    _cardZgoraj.PollStatusAsync(true),
                    _cardSpodaj.PollStatusAsync(true));
                await _cloudBridge.PublishStateAsync(true);
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

        public void SetCounterViewMode(int mode)
        {
            int normalized = mode == 1 ? 1 : 0;
            _config.CounterViewMode = normalized;
            _cardZgoraj.CounterViewMode = normalized;
            _cardSpodaj.CounterViewMode = normalized;
            _config.Save();
            RefreshSystemMenu();
        }

        private void ApplyFunicularTrackVisibility()
        {
            bool show = _config.ShowFunicularTrack;
            if (_funicularTrack != null)
            {
                _funicularTrack.Visibility = show ? Visibility.Visible : Visibility.Collapsed;
            }
            if (_rowFunicularTrack != null)
            {
                _rowFunicularTrack.Height = new GridLength(
                    show ? VisibleFunicularRowHeight : HiddenMiddleGapHeight,
                    GridUnitType.Pixel);
            }

            double perCardReduction = show ? ((VisibleFunicularRowHeight - HiddenMiddleGapHeight) * 0.5) : 0.0;
            if (_cardZgoraj != null)
                _cardZgoraj.ExtraVerticalHeightReduction = perCardReduction;
            if (_cardSpodaj != null)
                _cardSpodaj.ExtraVerticalHeightReduction = perCardReduction;
        }

        public void ToggleFunicularTrack()
        {
            _config.ShowFunicularTrack = !_config.ShowFunicularTrack;
            ApplyFunicularTrackVisibility();
            _config.Save();
            RefreshSystemMenu();
        }

        public void ToggleFunicularDirection(bool requireConfirmation = true)
        {
            if (requireConfirmation)
            {
                MessageBoxResult res = MessageBox.Show(
                    this,
                    "Ali želite obrniti smer prikaza tirnice (Zgornja ↔ Spodnja)?",
                    "Potrditev menjave smeri",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Question,
                    MessageBoxResult.No);

                if (res != MessageBoxResult.Yes)
                {
                    return;
                }
            }

            if (_funicularTrack != null)
            {
                _funicularTrack.ToggleDirection();
            }
            else
            {
                _config.FunicularTrackReversed = !_config.FunicularTrackReversed;
                _config.Save();
                RefreshSystemMenu();
            }
        }

        private void SyncFunicularPosition()
        {
            if (_funicularTrack == null) return;

            int totalSec = Math.Max(10, Math.Min(600, _config.RideDurationSec));
            long rideDurationMs = totalSec * 1000L;

            bool sOnline = _cardSpodaj != null && _cardSpodaj.IsOnline;
            long sRem = _cardSpodaj != null ? _cardSpodaj.RemainingMs : 0L;
            long sSet = _cardSpodaj != null ? _cardSpodaj.SetMs : 0L;

            bool zOnline = _cardZgoraj != null && _cardZgoraj.IsOnline;
            long zRem = _cardZgoraj != null ? _cardZgoraj.RemainingMs : 0L;
            long zSet = _cardZgoraj != null ? _cardZgoraj.SetMs : 0L;

            if (!sOnline && !zOnline)
            {
                bool isFromSpodnjaLast = string.Equals(_lastDepartedStationId, "spodaj", StringComparison.OrdinalIgnoreCase);
                _funicularTrack.SetRideState(false, false, isFromSpodnjaLast, _lastKnownSingleCarPos, 0L, totalSec);
                return;
            }

            long sElapsed = (sOnline && sSet >= rideDurationMs && sRem >= 1L && sRem < sSet) ? (sSet - sRem) : -1L;
            long zElapsed = (zOnline && zSet >= rideDurationMs && zRem >= 1L && zRem < zSet) ? (zSet - zRem) : -1L;

            string activeStationId;
            if (sElapsed > 0L && zElapsed > 0L)
            {
                if (sElapsed < zElapsed) activeStationId = "spodaj";
                else if (zElapsed < sElapsed) activeStationId = "zgoraj";
                else activeStationId = _lastDepartedStationId;
            }
            else if (sElapsed > 0L)
            {
                activeStationId = "spodaj";
            }
            else if (zElapsed > 0L)
            {
                activeStationId = "zgoraj";
            }
            else
            {
                activeStationId = _lastDepartedStationId;
            }
            _lastDepartedStationId = activeStationId;

            long activeElapsed = string.Equals(activeStationId, "spodaj", StringComparison.OrdinalIgnoreCase) ? sElapsed : zElapsed;
            bool isFromSpodnja = string.Equals(activeStationId, "spodaj", StringComparison.OrdinalIgnoreCase);

            if (activeElapsed >= 1L && activeElapsed <= rideDurationMs)
            {
                double progress = Math.Max(0.0, Math.Min(1.0, (double)activeElapsed / (double)rideDurationMs));
                double pos = isFromSpodnja ? progress : (1.0 - progress);
                _lastKnownSingleCarPos = pos;
                _funicularTrack.SetRideState(true, true, isFromSpodnja, pos, activeElapsed, totalSec);
            }
            else
            {
                double dockedPos;
                if (activeElapsed > rideDurationMs)
                {
                    dockedPos = isFromSpodnja ? 1.0 : 0.0;
                }
                else
                {
                    dockedPos = _lastKnownSingleCarPos;
                }
                _lastKnownSingleCarPos = dockedPos;
                _funicularTrack.SetRideState(true, false, isFromSpodnja, dockedPos, 0L, totalSec);
            }
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
                uint cloudFlag = _config.CloudRelayEnabled ? MF_CHECKED : MF_UNCHECKED;
                uint viewClassicFlag = _config.CounterViewMode == 0 ? MF_CHECKED : MF_UNCHECKED;
                uint viewUnderFlag = _config.CounterViewMode == 1 ? MF_CHECKED : MF_UNCHECKED;
                uint funicularTrackFlag = _config.ShowFunicularTrack ? MF_CHECKED : MF_UNCHECKED;
                uint funicularDirFlag = _config.FunicularTrackReversed ? MF_CHECKED : MF_UNCHECKED;
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | topFlag, (UIntPtr)IDM_ALWAYS_ON_TOP, "Vedno na vrhu");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | autostartFlag, (UIntPtr)IDM_START_WITH_WINDOWS, "Zaženi ob zagonu računalnika");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | soundFlag, (UIntPtr)IDM_SOUND_ENABLED, "Zvočna opozorila");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | voiceFlag, (UIntPtr)IDM_VOICE_ENABLED, "Glasovno obvestilo ob odhodu...");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | cloudFlag, (UIntPtr)IDM_CLOUD_SETTINGS, "Mobilni dostop (Oblak)...");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING, (UIntPtr)IDM_TOGGLE_CONTROLS, "Preklopi kontrole okna\t(F11)");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_SEPARATOR, UIntPtr.Zero, string.Empty);
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | viewClassicFlag, (UIntPtr)IDM_VIEW_CLASSIC, "Prikaz števcev: Klasični (ob številki)");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | viewUnderFlag, (UIntPtr)IDM_VIEW_UNDER, "Prikaz števcev: Pod številko (spodaj)");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | funicularTrackFlag, (UIntPtr)IDM_SHOW_FUNICULAR_TRACK, "Prikaz položaja vzpenjače");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING | funicularDirFlag, (UIntPtr)IDM_FUNICULAR_DIRECTION, "Obrni smer tirnice (Zgornja ↔ Spodnja)");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_SEPARATOR, UIntPtr.Zero, string.Empty);
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING, (UIntPtr)IDM_ZOOM_IN, "Povečaj števce\t(Ctrl +)");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING, (UIntPtr)IDM_ZOOM_OUT, "Pomanjšaj števce\t(Ctrl -)");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING, (UIntPtr)IDM_ZOOM_RESET, "Ponastavi velikost števcev\t(Ctrl 0)");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_SEPARATOR, UIntPtr.Zero, string.Empty);
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING, (UIntPtr)IDM_EXIT, "Izhod iz programa");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_SEPARATOR, UIntPtr.Zero, string.Empty);
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_STRING, (UIntPtr)IDM_VERSION, "Različica 1.2.3");
                InsertMenu(_hSysMenu, SC_CLOSE, MF_BYCOMMAND | MF_SEPARATOR, UIntPtr.Zero, string.Empty);
            }
        }

        private void InitializeTrayIcon()
        {
            try
            {
                _trayIcon = new Forms.NotifyIcon();
                _trayIcon.Text = "Nadzorna plošča vzpenjače";

                Drawing.Icon appIcon = null;
                try
                {
                    string exe = Process.GetCurrentProcess().MainModule.FileName;
                    if (File.Exists(exe))
                    {
                        appIcon = Drawing.Icon.ExtractAssociatedIcon(exe);
                    }
                }
                catch { }

                if (appIcon == null)
                {
                    try
                    {
                        string icoPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "app.ico");
                        if (File.Exists(icoPath))
                        {
                            appIcon = new Drawing.Icon(icoPath);
                        }
                    }
                    catch { }
                }

                if (appIcon == null)
                {
                    appIcon = Drawing.SystemIcons.Application;
                }

                _trayIcon.Icon = appIcon;

                try
                {
                    Icon = Imaging.CreateBitmapSourceFromHIcon(
                        appIcon.Handle,
                        Int32Rect.Empty,
                        System.Windows.Media.Imaging.BitmapSizeOptions.FromEmptyOptions());
                }
                catch { }

                Forms.ContextMenu trayMenu = new Forms.ContextMenu();

                Forms.MenuItem mnuOpen = new Forms.MenuItem("Prikaži Vzpenjačo", (s, e) => ShowAndRestoreWindow());
                mnuOpen.DefaultItem = true;

                Forms.MenuItem mnuTop = new Forms.MenuItem("Vedno na vrhu", (s, e) => ToggleAlwaysOnTop());
                Forms.MenuItem mnuSound = new Forms.MenuItem("Zvočna opozorila", (s, e) => ToggleSound());
                Forms.MenuItem mnuViewClassic = new Forms.MenuItem("Prikaz števcev: Klasični", (s, e) => SetCounterViewMode(0));
                Forms.MenuItem mnuViewUnder = new Forms.MenuItem("Prikaz števcev: Pod številko", (s, e) => SetCounterViewMode(1));
                Forms.MenuItem mnuFunicularTrack = new Forms.MenuItem("Prikaz položaja vzpenjače", (s, e) => ToggleFunicularTrack());
                Forms.MenuItem mnuFunicularDir = new Forms.MenuItem("Obrni smer tirnice (Zgornja ↔ Spodnja)", (s, e) => ToggleFunicularDirection());
                Forms.MenuItem mnuExit = new Forms.MenuItem("Izhod", (s, e) => ExitApplication());

                trayMenu.Popup += (s, e) =>
                {
                    mnuTop.Checked = Topmost;
                    mnuSound.Checked = _config.SoundEnabled;
                    mnuViewClassic.Checked = (_config.CounterViewMode == 0);
                    mnuViewUnder.Checked = (_config.CounterViewMode == 1);
                    mnuFunicularTrack.Checked = _config.ShowFunicularTrack;
                    mnuFunicularDir.Checked = _config.FunicularTrackReversed;
                    mnuFunicularDir.Enabled = _config.ShowFunicularTrack;
                };

                trayMenu.MenuItems.Add(mnuOpen);
                trayMenu.MenuItems.Add("-");
                trayMenu.MenuItems.Add(mnuTop);
                trayMenu.MenuItems.Add(mnuSound);
                trayMenu.MenuItems.Add("-");
                trayMenu.MenuItems.Add(mnuViewClassic);
                trayMenu.MenuItems.Add(mnuViewUnder);
                trayMenu.MenuItems.Add(mnuFunicularTrack);
                trayMenu.MenuItems.Add(mnuFunicularDir);
                trayMenu.MenuItems.Add("-");
                trayMenu.MenuItems.Add(mnuExit);

                _trayIcon.ContextMenu = trayMenu;

                _trayIcon.MouseClick += (s, e) =>
                {
                    if (e.Button == Forms.MouseButtons.Left)
                    {
                        ShowAndRestoreWindow();
                    }
                };

                _trayIcon.DoubleClick += (s, e) =>
                {
                    ShowAndRestoreWindow();
                };

                _trayIcon.BalloonTipClicked += (s, e) =>
                {
                    ShowAndRestoreWindow();
                };

                _trayIcon.Visible = true;
            }
            catch { }
        }

        private void ShowTrayNotificationOnce()
        {
            if (!_hasShownTrayBalloon && _trayIcon != null)
            {
                _hasShownTrayBalloon = true;
                try
                {
                    _trayIcon.ShowBalloonTip(
                        3500,
                        "Vzpenjača",
                        "Program se še naprej izvaja v ozadju v sistemski vrstici (ob uri).\nZa ponovni prikaz kliknite ikono, za izhod pa z desno tipko miške izberite »Izhod«.",
                        Forms.ToolTipIcon.Info);
                }
                catch { }
            }
        }

        private void DisposeTrayIcon()
        {
            try
            {
                if (_trayIcon != null)
                {
                    _trayIcon.Visible = false;
                    _trayIcon.Dispose();
                    _trayIcon = null;
                }
            }
            catch { }
        }

        public void ExitApplication()
        {
            _isExplicitExit = true;
            DisposeTrayIcon();
            Close();
            try
            {
                Application.Current.Shutdown();
            }
            catch { }
        }

        public void ShowAndRestoreWindow()
        {
            if (!Dispatcher.CheckAccess())
            {
                Dispatcher.BeginInvoke((Action)ShowAndRestoreWindow);
                return;
            }

            if (!IsVisible)
            {
                Show();
            }
            if (WindowState == WindowState.Minimized)
            {
                WindowState = WindowState.Normal;
            }
            Activate();
            Focus();
            IntPtr hwnd = new WindowInteropHelper(this).Handle;
            if (hwnd != IntPtr.Zero)
            {
                SetForegroundWindow(hwnd);
            }
        }

        public void ToggleAlwaysOnTop()
        {
            Topmost = !Topmost;
            _config.AlwaysOnTop = Topmost;
            _config.Save();

            if (_hSysMenu != IntPtr.Zero)
            {
                CheckMenuItem(_hSysMenu, IDM_ALWAYS_ON_TOP, MF_BYCOMMAND | (Topmost ? MF_CHECKED : MF_UNCHECKED));
            }
        }

        public void ToggleSound()
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
        }

        private IntPtr WndProc(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
        {
            if (msg == WM_QUERYENDSESSION || msg == WM_ENDSESSION)
            {
                _isExplicitExit = true;
                return new IntPtr(1);
            }

            if (msg == WM_SYSCOMMAND)
            {
                int cmd = wParam.ToInt32() & 0xFFFF;
                if (cmd == IDM_ALWAYS_ON_TOP)
                {
                    ToggleAlwaysOnTop();
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
                    ToggleSound();
                    handled = true;
                }
                else if (cmd == IDM_VOICE_ENABLED)
                {
                    VoiceSettingsDialog dlg = new VoiceSettingsDialog(this, _config);
                    dlg.ShowDialog();
                    RefreshSystemMenu();
                    handled = true;
                }
                else if (cmd == IDM_CLOUD_SETTINGS)
                {
                    CloudSettingsDialog dlg = new CloudSettingsDialog(this, _config, _cloudBridge);
                    dlg.ShowDialog();
                    RefreshSystemMenu();
                    handled = true;
                }
                else if (cmd == IDM_TOGGLE_CONTROLS)
                {
                    ToggleWindowControls();
                    handled = true;
                }
                else if (cmd == IDM_VIEW_CLASSIC)
                {
                    SetCounterViewMode(0);
                    handled = true;
                }
                else if (cmd == IDM_VIEW_UNDER)
                {
                    SetCounterViewMode(1);
                    handled = true;
                }
                else if (cmd == IDM_SHOW_FUNICULAR_TRACK)
                {
                    ToggleFunicularTrack();
                    handled = true;
                }
                else if (cmd == IDM_FUNICULAR_DIRECTION)
                {
                    ToggleFunicularDirection();
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
                else if (cmd == IDM_EXIT)
                {
                    ExitApplication();
                    handled = true;
                }
                else if (cmd == IDM_VERSION)
                {
                    MessageBox.Show(
                        "Nadzorna plošča vzpenjače\nRazličica 1.2.3\n\nZgornja postaja: " + _config.IpZgoraj + "\nSpodnja postaja: " + _config.IpSpodaj + "\nMobilni kanal: " + _config.CloudChannelId,
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
        private static EventWaitHandle _activateEvent;

        [STAThread]
        static void Main()
        {
            bool createdNew;
            try
            {
                _activateEvent = new EventWaitHandle(false, EventResetMode.AutoReset, "VzpenjacaKontrola_SingleInstance_Signal", out createdNew);
            }
            catch
            {
                createdNew = true;
            }

            if (!createdNew)
            {
                try
                {
                    _activateEvent.Set();
                }
                catch { }
                return;
            }

            Application app = new Application();
            MainWindow mainWindow = new MainWindow();

            Task.Factory.StartNew(() =>
            {
                while (true)
                {
                    try
                    {
                        if (_activateEvent != null && _activateEvent.WaitOne())
                        {
                            mainWindow.ShowAndRestoreWindow();
                        }
                    }
                    catch
                    {
                        break;
                    }
                }
            }, TaskCreationOptions.LongRunning);

            app.Run(mainWindow);
        }
    }
}
