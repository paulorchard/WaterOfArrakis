# Generates the HUD bar textures in the style of the vanilla health and stamina bars, vertical, for the bars on the screen
# edges. Measured from vanilla (636 x 24, the @2x of 318 x 12): background #341611 with a lighter #4d322d rim on the two
# long edges, a fill with a lighter rim and a gradient, and one end cut at an angle. Our fill is white so the client can
# tint it (Background.Color), and a separate sheen overlay carries the rim and the gloss, so the colour can change
# without losing the style.
#
# Orientation: the bar is upright. The angled cap is at the TOP, the BOTTOM is flat and square. The cap is cut at exactly
# 45 degrees: it is as many texture pixels tall as the bar is wide ($CAP = $WIDTH). The "Left" textures are for the bar
# on the left screen edge and have the top-LEFT corner cut (the cap slopes down toward the screen edge); the "Right"
# textures are their mirror image for the bar on the right edge. In the UI the textures are 9-sliced vertically
# (VerticalBorder = $CAP / 2, the cap in on-screen pixels), so only the straight middle stretches and the cap keeps its
# 45 degrees at every screen height. If the bar thickness or cap size is changed, change $WIDTH here and CapSize in the
# .ui file together.
#   powershell -File tools/assets/make_bars.ps1
Add-Type -AssemblyName System.Drawing
$out = Join-Path $PSScriptRoot '..\..\src\main\resources\Common\UI\Custom\Hud\Water_of_Arrakis'
$WIDTH = 24     # texture pixels across (the @2x of the 12 px on-screen thickness)
$CAP = 24       # texture pixels tall for the angled cap; equal to $WIDTH gives exactly 45 degrees
$LENGTH = 636   # texture pixels long (the 9-slice stretches the middle, so any length works)
$bg = [System.Drawing.Color]::FromArgb(255, 0x34, 0x16, 0x11)
$rim = [System.Drawing.Color]::FromArgb(255, 0x4d, 0x32, 0x2d)

# Is pixel (x, y) inside the shape? The cut removes the triangle with x + y < CAP (left) or (WIDTH - 1 - x) + y < CAP (right).
function Inside($x, $y, $left) {
    $d = if ($left) { $x } else { $WIDTH - 1 - $x }
    return ($d + $y) -ge ($CAP - 1)
}
function Rim($x) { return ($x -le 4 -or $x -ge ($WIDTH - 5)) }

function Make($name, $left, $pixel) {
    $bmp = New-Object System.Drawing.Bitmap $WIDTH, $LENGTH, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $LENGTH; $y++) {
        for ($x = 0; $x -lt $WIDTH; $x++) {
            if (Inside $x $y $left) { $bmp.SetPixel($x, $y, (& $pixel $x $y)) }
            else { $bmp.SetPixel($x, $y, [System.Drawing.Color]::Transparent) }
        }
    }
    $bmp.Save((Join-Path $out $name), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

foreach ($left in $true, $false) {
    $side = if ($left) { 'Left' } else { 'Right' }
    Make "Arrakis_Bar_V_Background_$side@2x.png" $left { param($x, $y) if (Rim $x) { $rim } else { $bg } }
    # White body, a little darker toward the top so the tint reads as a gradient.
    Make "Arrakis_Bar_V_Fill_$side@2x.png" $left {
        param($x, $y)
        $v = [int](255 - 40 * (1 - $y / ($LENGTH - 1)))
        [System.Drawing.Color]::FromArgb(255, $v, $v, $v)
    }
    # Sheen: bright rim, a soft gloss over the inner half, a faint shade over the outer half.
    Make "Arrakis_Bar_V_Sheen_$side@2x.png" $left {
        param($x, $y)
        $inner = if ($left) { $x } else { $WIDTH - 1 - $x }
        if (Rim $x) { [System.Drawing.Color]::FromArgb(110, 255, 255, 255) }
        elseif ($inner -le 11) { [System.Drawing.Color]::FromArgb(36, 255, 255, 255) }
        else { [System.Drawing.Color]::FromArgb(28, 0, 0, 0) }
    }
}
Get-ChildItem $out -Filter '*.png' | Select-Object Name, Length
