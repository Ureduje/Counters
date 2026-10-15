using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

class GenerateIcon
{
    static void Main(string[] args)
    {
        string outPath = args.Length > 0 ? args[0] : "app.ico";
        using (Bitmap bmp = new Bitmap(64, 64, PixelFormat.Format32bppArgb))
        using (Graphics g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Color.Transparent);

            // Background rounded badge
            using (GraphicsPath bgPath = RoundedRect(new Rectangle(2, 2, 60, 60), 14))
            using (LinearGradientBrush bgBrush = new LinearGradientBrush(
                new Rectangle(2, 2, 60, 60),
                Color.FromArgb(30, 41, 59),
                Color.FromArgb(15, 23, 42),
                LinearGradientMode.Vertical))
            using (Pen borderPen = new Pen(Color.FromArgb(56, 189, 248), 2.5f))
            {
                g.FillPath(bgBrush, bgPath);
                g.DrawPath(borderPen, bgPath);
            }

            // Diagonal funicular track line
            using (Pen trackPen = new Pen(Color.FromArgb(148, 163, 184), 3f))
            {
                g.DrawLine(trackPen, 10, 50, 54, 18);
            }

            // Funicular cabin
            using (GraphicsPath cabinPath = RoundedRect(new Rectangle(18, 20, 28, 22), 5))
            using (LinearGradientBrush cabinBrush = new LinearGradientBrush(
                new Rectangle(18, 20, 28, 22),
                Color.FromArgb(245, 158, 11),
                Color.FromArgb(217, 119, 6),
                LinearGradientMode.Vertical))
            using (Pen cabinPen = new Pen(Color.FromArgb(254, 243, 199), 1.5f))
            {
                g.FillPath(cabinBrush, cabinPath);
                g.DrawPath(cabinPen, cabinPath);
            }

            // Cabin windows
            using (Brush winBrush = new SolidBrush(Color.FromArgb(15, 23, 42)))
            {
                g.FillRectangle(winBrush, 22, 24, 8, 9);
                g.FillRectangle(winBrush, 34, 24, 8, 9);
            }

            // Status green dot
            using (Brush dotBrush = new SolidBrush(Color.FromArgb(34, 197, 94)))
            {
                g.FillEllipse(dotBrush, 46, 44, 10, 10);
            }

            SavePngAsIcon(bmp, outPath);
        }
    }

    static GraphicsPath RoundedRect(Rectangle bounds, int radius)
    {
        int d = radius * 2;
        GraphicsPath path = new GraphicsPath();
        path.AddArc(bounds.Left, bounds.Top, d, d, 180, 90);
        path.AddArc(bounds.Right - d, bounds.Top, d, d, 270, 90);
        path.AddArc(bounds.Right - d, bounds.Bottom - d, d, d, 0, 90);
        path.AddArc(bounds.Left, bounds.Bottom - d, d, d, 90, 90);
        path.CloseFigure();
        return path;
    }

    static void SavePngAsIcon(Bitmap bmp, string filePath)
    {
        byte[] pngBytes;
        using (MemoryStream ms = new MemoryStream())
        {
            bmp.Save(ms, ImageFormat.Png);
            pngBytes = ms.ToArray();
        }

        using (FileStream fs = new FileStream(filePath, FileMode.Create, FileAccess.Write))
        using (BinaryWriter bw = new BinaryWriter(fs))
        {
            // ICONDIR header
            bw.Write((short)0);   // Reserved
            bw.Write((short)1);   // Type: 1 = ICO
            bw.Write((short)1);   // Count: 1 image

            // ICONDIRENTRY
            bw.Write((byte)bmp.Width);
            bw.Write((byte)bmp.Height);
            bw.Write((byte)0);    // Color count
            bw.Write((byte)0);    // Reserved
            bw.Write((short)1);   // Color planes
            bw.Write((short)32);  // Bits per pixel
            bw.Write(pngBytes.Length); // Size of image data
            bw.Write(22);         // Offset of image data (6 + 16 = 22)

            bw.Write(pngBytes);
        }
    }
}
