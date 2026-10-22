using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Xml.Linq;

/// <summary>
/// Generates multi-resolution Windows app icon (app.ico) inherited directly
/// from the Android application vector drawable (AndroidApp/app/src/main/res/drawable/ic_launcher.xml).
/// </summary>
public class GenerateIcon
{
    private class VectorPathItem
    {
        public Geometry Geometry;
        public Brush FillBrush;
        public Pen StrokePen;
    }

    private class VectorDrawableData
    {
        public float ViewportWidth = 108f;
        public float ViewportHeight = 108f;
        public List<VectorPathItem> Paths = new List<VectorPathItem>();
    }

    [STAThread]
    public static void Main(string[] args)
    {
        string outIcoPath = args.Length > 0 && !string.IsNullOrWhiteSpace(args[0]) ? args[0] : "app.ico";
        string xmlSourcePath = args.Length > 1 && !string.IsNullOrWhiteSpace(args[1]) ? args[1] : null;
        string outPngPreview = args.Length > 2 && !string.IsNullOrWhiteSpace(args[2]) ? args[2] : null;

        string resolvedXml = FindAndroidIconXml(xmlSourcePath);
        if (string.IsNullOrEmpty(resolvedXml) || !File.Exists(resolvedXml))
        {
            throw new FileNotFoundException("Datoteke ikone Android aplikacije (ic_launcher.xml) ni mogoče najti: " + (xmlSourcePath ?? "ic_launcher.xml"));
        }

        Console.WriteLine("Prevzem ikone iz Android aplikacije: " + resolvedXml);
        VectorDrawableData vectorData = ParseVectorDrawable(resolvedXml);
        Console.WriteLine("Nalozenih poti (vector paths): " + vectorData.Paths.Count + " (viewport: " + vectorData.ViewportWidth + "x" + vectorData.ViewportHeight + ")");

        // Standard Windows icon sizes (Vista - Windows 11)
        int[] iconSizes = new int[] { 256, 128, 64, 48, 32, 24, 16 };
        List<byte[]> encodedPngs = new List<byte[]>();

        foreach (int sz in iconSizes)
        {
            byte[] pngBytes = RenderIconFramePng(vectorData, sz);
            encodedPngs.Add(pngBytes);
        }

        SaveMultiSizeIcon(iconSizes, encodedPngs, outIcoPath);
        Console.WriteLine("Uspešno zgrajena večstopenjska ikona: " + outIcoPath + " (" + new FileInfo(outIcoPath).Length + " bajtov)");

        if (!string.IsNullOrEmpty(outPngPreview))
        {
            byte[] previewPng = RenderIconFramePng(vectorData, 512);
            File.WriteAllBytes(outPngPreview, previewPng);
            Console.WriteLine("Ustvarjen predogled ikone: " + outPngPreview);
        }
    }

    public static string FindAndroidIconXml(string explicitPath)
    {
        if (!string.IsNullOrEmpty(explicitPath) && File.Exists(explicitPath))
            return Path.GetFullPath(explicitPath);

        string[] candidates = new string[]
        {
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, @"AndroidApp\app\src\main\res\drawable\ic_launcher.xml"),
            Path.Combine(Directory.GetCurrentDirectory(), @"AndroidApp\app\src\main\res\drawable\ic_launcher.xml"),
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, @"..\AndroidApp\app\src\main\res\drawable\ic_launcher.xml"),
            @"AndroidApp\app\src\main\res\drawable\ic_launcher.xml"
        };

        foreach (string candidate in candidates)
        {
            if (File.Exists(candidate))
                return Path.GetFullPath(candidate);
        }

        return null;
    }

    private static VectorDrawableData ParseVectorDrawable(string xmlPath)
    {
        XDocument doc = XDocument.Load(xmlPath);
        XElement vectorElem = doc.Root;
        if (vectorElem == null) throw new InvalidOperationException("Neveljaven korenski element v " + xmlPath);

        XNamespace androidNs = "http://schemas.android.com/apk/res/android";
        VectorDrawableData data = new VectorDrawableData();

        string vpWStr = GetAttr(vectorElem, androidNs, "viewportWidth");
        string vpHStr = GetAttr(vectorElem, androidNs, "viewportHeight");

        if (!string.IsNullOrEmpty(vpWStr)) float.TryParse(vpWStr, NumberStyles.Float, CultureInfo.InvariantCulture, out data.ViewportWidth);
        if (!string.IsNullOrEmpty(vpHStr)) float.TryParse(vpHStr, NumberStyles.Float, CultureInfo.InvariantCulture, out data.ViewportHeight);

        foreach (XElement pathElem in vectorElem.Descendants())
        {
            if (!pathElem.Name.LocalName.Equals("path", StringComparison.OrdinalIgnoreCase))
                continue;

            string pathData = GetAttr(pathElem, androidNs, "pathData");
            if (string.IsNullOrWhiteSpace(pathData))
                continue;

            Geometry geom = Geometry.Parse(pathData);
            geom.Freeze();

            Brush fillBrush = null;
            string fillColor = GetAttr(pathElem, androidNs, "fillColor");
            if (!string.IsNullOrWhiteSpace(fillColor))
            {
                Color c = ParseColor(fillColor);
                if (c.A > 0)
                {
                    SolidColorBrush scb = new SolidColorBrush(c);
                    scb.Freeze();
                    fillBrush = scb;
                }
            }

            Pen strokePen = null;
            string strokeColor = GetAttr(pathElem, androidNs, "strokeColor");
            if (!string.IsNullOrWhiteSpace(strokeColor))
            {
                Color sc = ParseColor(strokeColor);
                if (sc.A > 0)
                {
                    SolidColorBrush strokeBrush = new SolidColorBrush(sc);
                    strokeBrush.Freeze();

                    float strokeWidth = 1f;
                    string strokeWidthStr = GetAttr(pathElem, androidNs, "strokeWidth");
                    if (!string.IsNullOrEmpty(strokeWidthStr))
                    {
                        float.TryParse(strokeWidthStr, NumberStyles.Float, CultureInfo.InvariantCulture, out strokeWidth);
                    }

                    strokePen = new Pen(strokeBrush, strokeWidth);

                    string cap = GetAttr(pathElem, androidNs, "strokeLineCap");
                    if (string.Equals(cap, "round", StringComparison.OrdinalIgnoreCase))
                    {
                        strokePen.StartLineCap = PenLineCap.Round;
                        strokePen.EndLineCap = PenLineCap.Round;
                    }
                    else if (string.Equals(cap, "square", StringComparison.OrdinalIgnoreCase))
                    {
                        strokePen.StartLineCap = PenLineCap.Square;
                        strokePen.EndLineCap = PenLineCap.Square;
                    }

                    string join = GetAttr(pathElem, androidNs, "strokeLineJoin");
                    if (string.Equals(join, "round", StringComparison.OrdinalIgnoreCase))
                    {
                        strokePen.LineJoin = PenLineJoin.Round;
                    }
                    else if (string.Equals(join, "bevel", StringComparison.OrdinalIgnoreCase))
                    {
                        strokePen.LineJoin = PenLineJoin.Bevel;
                    }

                    strokePen.Freeze();
                }
            }

            data.Paths.Add(new VectorPathItem
            {
                Geometry = geom,
                FillBrush = fillBrush,
                StrokePen = strokePen
            });
        }

        return data;
    }

    private static string GetAttr(XElement elem, XNamespace ns, string localName)
    {
        XAttribute attr = elem.Attribute(ns + localName) ?? elem.Attribute(localName);
        return attr != null ? attr.Value : null;
    }

    private static Color ParseColor(string hex)
    {
        hex = hex.Trim().TrimStart('#');
        if (hex.Length == 8)
        {
            byte a = byte.Parse(hex.Substring(0, 2), NumberStyles.HexNumber);
            byte r = byte.Parse(hex.Substring(2, 2), NumberStyles.HexNumber);
            byte g = byte.Parse(hex.Substring(4, 2), NumberStyles.HexNumber);
            byte b = byte.Parse(hex.Substring(6, 2), NumberStyles.HexNumber);
            return Color.FromArgb(a, r, g, b);
        }
        if (hex.Length == 6)
        {
            byte r = byte.Parse(hex.Substring(0, 2), NumberStyles.HexNumber);
            byte g = byte.Parse(hex.Substring(2, 2), NumberStyles.HexNumber);
            byte b = byte.Parse(hex.Substring(4, 2), NumberStyles.HexNumber);
            return Color.FromArgb(255, r, g, b);
        }
        if (hex.Length == 3)
        {
            byte r = byte.Parse(new string(hex[0], 2), NumberStyles.HexNumber);
            byte g = byte.Parse(new string(hex[1], 2), NumberStyles.HexNumber);
            byte b = byte.Parse(new string(hex[2], 2), NumberStyles.HexNumber);
            return Color.FromArgb(255, r, g, b);
        }
        return Colors.Transparent;
    }

    private static byte[] RenderIconFramePng(VectorDrawableData vectorData, int pixelSize)
    {
        double vpW = vectorData.ViewportWidth > 0 ? vectorData.ViewportWidth : 108.0;
        double vpH = vectorData.ViewportHeight > 0 ? vectorData.ViewportHeight : 108.0;

        DrawingVisual dv = new DrawingVisual();
        using (DrawingContext dc = dv.RenderOpen())
        {
            // Soft rounded squircle badge clip matching modern Windows 11 / Android adaptive icons
            double pad = pixelSize * (3.5 / 108.0);
            double badgeSize = pixelSize - (pad * 2.0);
            double cornerRadius = pixelSize * (22.5 / 108.0);

            RectangleGeometry clipGeo = new RectangleGeometry(
                new Rect(pad, pad, badgeSize, badgeSize),
                cornerRadius, cornerRadius);
            clipGeo.Freeze();

            dc.PushClip(clipGeo);

            // Scale from Android viewport coordinate space (108x108) to target icon pixel size
            double scaleX = pixelSize / vpW;
            double scaleY = pixelSize / vpH;
            ScaleTransform st = new ScaleTransform(scaleX, scaleY);
            st.Freeze();
            dc.PushTransform(st);

            // Render all vector drawable paths in exact order as defined in Android ic_launcher.xml
            foreach (VectorPathItem item in vectorData.Paths)
            {
                dc.DrawGeometry(item.FillBrush, item.StrokePen, item.Geometry);
            }

            dc.Pop(); // ScaleTransform
            dc.Pop(); // Clip

            // Subtle perimeter border around the rounded badge for crisp contrast on light/dark themes
            Pen borderPen = new Pen(
                new SolidColorBrush(Color.FromArgb(255, 34, 52, 78)),
                Math.Max(1.0, pixelSize * (1.8 / 108.0)));
            borderPen.Freeze();
            dc.DrawGeometry(null, borderPen, clipGeo);
        }

        RenderTargetBitmap rtb = new RenderTargetBitmap(pixelSize, pixelSize, 96, 96, PixelFormats.Pbgra32);
        rtb.Render(dv);

        PngBitmapEncoder encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(rtb));

        using (MemoryStream ms = new MemoryStream())
        {
            encoder.Save(ms);
            return ms.ToArray();
        }
    }

    private static void SaveMultiSizeIcon(int[] sizes, List<byte[]> pngDataList, string filePath)
    {
        using (FileStream fs = new FileStream(filePath, FileMode.Create, FileAccess.Write))
        using (BinaryWriter bw = new BinaryWriter(fs))
        {
            int count = sizes.Length;
            bw.Write((short)0);   // Reserved
            bw.Write((short)1);   // Type: 1 = ICO
            bw.Write((short)count);

            int dataOffset = 6 + count * 16;
            for (int i = 0; i < count; i++)
            {
                int sz = sizes[i];
                byte[] pngBytes = pngDataList[i];
                bw.Write((byte)(sz >= 256 ? 0 : sz)); // Width (0 means 256)
                bw.Write((byte)(sz >= 256 ? 0 : sz)); // Height (0 means 256)
                bw.Write((byte)0);                    // Color count
                bw.Write((byte)0);                    // Reserved
                bw.Write((short)1);                   // Color planes
                bw.Write((short)32);                  // Bits per pixel
                bw.Write(pngBytes.Length);            // Size of image data
                bw.Write(dataOffset);                 // Offset of image data
                dataOffset += pngBytes.Length;
            }

            for (int i = 0; i < count; i++)
            {
                bw.Write(pngDataList[i]);
            }
        }
    }
}
