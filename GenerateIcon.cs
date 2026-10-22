using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

public class GenerateIcon
{
    public static void Main(string[] args)
    {
        string outIcoPath = args.Length > 0 ? args[0] : "app.ico";
        string outPngPath = args.Length > 1 ? args[1] : null;

        int[] iconSizes = new int[] { 256, 64, 48, 32, 16 };
        List<byte[]> encodedPngs = new List<byte[]>();

        foreach (int sz in iconSizes)
        {
            using (Bitmap bmp = RenderUnifiedIcon(sz))
            using (MemoryStream ms = new MemoryStream())
            {
                bmp.Save(ms, ImageFormat.Png);
                encodedPngs.Add(ms.ToArray());
            }
        }

        SaveMultiSizeIcon(iconSizes, encodedPngs, outIcoPath);

        if (!string.IsNullOrEmpty(outPngPath))
        {
            using (Bitmap previewBmp = RenderUnifiedIcon(512))
            {
                previewBmp.Save(outPngPath, ImageFormat.Png);
            }
        }
    }

    static Bitmap RenderUnifiedIcon(int pixelSize)
    {
        Bitmap bmp = new Bitmap(pixelSize, pixelSize, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = SmoothingMode.HighQuality;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.Clear(Color.Transparent);

            // Unified 108x108 coordinate space shared across PC, Android, and Wear OS
            float s = pixelSize / 108f;

            // 1. Rounded Dark Slate Badge Background
            RectangleF bgRect = new RectangleF(4f * s, 4f * s, 100f * s, 100f * s);
            RectangleF borderRect = new RectangleF(5.2f * s, 5.2f * s, 97.6f * s, 97.6f * s);
            using (GraphicsPath bgPath = RoundedRectF(bgRect, 24f * s))
            using (GraphicsPath borderPath = RoundedRectF(borderRect, 22.8f * s))
            using (LinearGradientBrush bgBrush = new LinearGradientBrush(
                bgRect,
                Color.FromArgb(20, 29, 47),   // #141D2F
                Color.FromArgb(11, 15, 23),   // #0B0F17
                LinearGradientMode.Vertical))
            using (Pen bgBorderPen = new Pen(Color.FromArgb(34, 52, 78), Math.Max(1f, 2.2f * s))) // #22344E
            {
                g.FillPath(bgBrush, bgPath);
                g.DrawPath(bgBorderPen, borderPath);
            }

            // 2. Top Display Card (Combines Time on left + Number on right)
            RectangleF cardRect = new RectangleF(21f * s, 19f * s, 66f * s, 28f * s);
            using (GraphicsPath cardPath = RoundedRectF(cardRect, 9f * s))
            using (SolidBrush cardBrush = new SolidBrush(Color.FromArgb(24, 35, 54))) // #182336
            using (Pen cardPen = new Pen(Color.FromArgb(56, 189, 248), Math.Max(1.2f, 3.0f * s))) // #38BDF8
            {
                g.FillPath(cardBrush, cardPath);
                g.DrawPath(cardPen, cardPen.Width > 0 ? cardPath : cardPath);
            }

            // Center vertical divider inside top display card
            using (Pen divPen = new Pen(Color.FromArgb(42, 60, 88), Math.Max(1f, 2.0f * s))) // #2A3C58
            {
                divPen.StartCap = LineCap.Round;
                divPen.EndCap = LineCap.Round;
                g.DrawLine(divPen, 54f * s, 24.5f * s, 54f * s, 41.5f * s);
            }

            // 2a. Left Half: Stopwatch / Clock (Time Countdown)
            float clockCx = 38f * s;
            float clockCy = 34.2f * s;
            float clockR = 7.4f * s;

            using (Pen clockRingPen = new Pen(Color.FromArgb(56, 189, 248), Math.Max(1.1f, 2.4f * s))) // #38BDF8
            {
                clockRingPen.StartCap = LineCap.Round;
                clockRingPen.EndCap = LineCap.Round;
                g.DrawEllipse(clockRingPen, clockCx - clockR, clockCy - clockR, clockR * 2f, clockR * 2f);
                // Top stopwatch crown button
                g.DrawLine(clockRingPen, 36f * s, 24.2f * s, 40f * s, 24.2f * s);
            }

            using (Pen handPen = new Pen(Color.FromArgb(248, 250, 252), Math.Max(1.1f, 2.3f * s))) // #F8FAFC
            {
                handPen.StartCap = LineCap.Round;
                handPen.EndCap = LineCap.Round;
                handPen.LineJoin = LineJoin.Round;
                PointF[] handPts = new PointF[]
                {
                    new PointF(clockCx, clockCy - 4.1f * s),
                    new PointF(clockCx, clockCy),
                    new PointF(clockCx + 3.4f * s, clockCy + 2.2f * s)
                };
                g.DrawLines(handPen, handPts);
            }

            // 2b. Right Half: Bold "+1" Number Counter
            using (Pen numPen = new Pen(Color.FromArgb(16, 185, 129), Math.Max(1.3f, 3.5f * s))) // #10B981
            {
                numPen.StartCap = LineCap.Round;
                numPen.EndCap = LineCap.Round;
                numPen.LineJoin = LineJoin.Round;

                // "+" symbol
                g.DrawLine(numPen, 63.8f * s, 29f * s, 63.8f * s, 37.4f * s);
                g.DrawLine(numPen, 59.6f * s, 33.2f * s, 68f * s, 33.2f * s);

                // "1" digit
                PointF[] onePts = new PointF[]
                {
                    new PointF(72.8f * s, 30.4f * s),
                    new PointF(76.5f * s, 27.7f * s),
                    new PointF(76.5f * s, 38.7f * s)
                };
                g.DrawLines(numPen, onePts);
            }

            // 3. Bottom Half: Simple Access Gate
            RectangleF leftPillar = new RectangleF(24f * s, 56f * s, 12f * s, 30f * s);
            RectangleF rightPillar = new RectangleF(72f * s, 56f * s, 12f * s, 30f * s);
            using (GraphicsPath lpPath = RoundedRectF(leftPillar, 4.5f * s))
            using (GraphicsPath rpPath = RoundedRectF(rightPillar, 4.5f * s))
            using (SolidBrush pillarBrush = new SolidBrush(Color.FromArgb(56, 189, 248))) // #38BDF8
            {
                g.FillPath(pillarBrush, lpPath);
                g.FillPath(pillarBrush, rpPath);
            }

            // Dark halo behind barrier arm where it crosses the left pillar
            using (Pen armHaloPen = new Pen(Color.FromArgb(11, 15, 23), Math.Max(2f, 10.2f * s)))
            {
                armHaloPen.StartCap = LineCap.Round;
                armHaloPen.EndCap = LineCap.Round;
                g.DrawLine(armHaloPen, 30f * s, 72f * s, 66f * s, 59f * s);
            }

            // Gate Barrier Arm (#10B981 Emerald)
            using (Pen armPen = new Pen(Color.FromArgb(16, 185, 129), Math.Max(1.6f, 7.0f * s)))
            {
                armPen.StartCap = LineCap.Round;
                armPen.EndCap = LineCap.Round;
                g.DrawLine(armPen, 30f * s, 72f * s, 66f * s, 59f * s);
            }

            // Barrier Pivot Point
            float pivotR = 2.6f * s;
            using (SolidBrush pivotBrush = new SolidBrush(Color.FromArgb(11, 15, 23)))
            {
                g.FillEllipse(pivotBrush, 30f * s - pivotR, 72f * s - pivotR, pivotR * 2f, pivotR * 2f);
            }
        }
        return bmp;
    }

    static GraphicsPath RoundedRectF(RectangleF bounds, float radius)
    {
        float d = radius * 2f;
        GraphicsPath path = new GraphicsPath();
        path.AddArc(bounds.Left, bounds.Top, d, d, 180, 90);
        path.AddArc(bounds.Right - d, bounds.Top, d, d, 270, 90);
        path.AddArc(bounds.Right - d, bounds.Bottom - d, d, d, 0, 90);
        path.AddArc(bounds.Left, bounds.Bottom - d, d, d, 90, 90);
        path.CloseFigure();
        return path;
    }

    static void SaveMultiSizeIcon(int[] sizes, List<byte[]> pngDataList, string filePath)
    {
        using (FileStream fs = new FileStream(filePath, FileMode.Create, FileAccess.Write))
        using (BinaryWriter bw = new BinaryWriter(fs))
        {
            int count = sizes.Length;
            bw.Write((short)0);
            bw.Write((short)1);
            bw.Write((short)count);

            int dataOffset = 6 + count * 16;
            for (int i = 0; i < count; i++)
            {
                int sz = sizes[i];
                byte[] pngBytes = pngDataList[i];
                bw.Write((byte)(sz >= 256 ? 0 : sz));
                bw.Write((byte)(sz >= 256 ? 0 : sz));
                bw.Write((byte)0);
                bw.Write((byte)0);
                bw.Write((short)1);
                bw.Write((short)32);
                bw.Write(pngBytes.Length);
                bw.Write(dataOffset);
                dataOffset += pngBytes.Length;
            }

            for (int i = 0; i < count; i++)
            {
                bw.Write(pngDataList[i]);
            }
        }
    }
}
