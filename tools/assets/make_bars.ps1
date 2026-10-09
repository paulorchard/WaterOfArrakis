# Generates the HUD bar textures in the style of the vanilla health and stamina bars (636 x 24, the @2x of 318 x 12).
# Measured from vanilla: the outer end is cut on a slant, top row 16 px in, bottom row flush (Health: left end,
# Stamina: right end). Background #341611 with a lighter #4d322d rim on the top and bottom rows. The fill has a
# lighter rim on the same rows and a gradient. Our fill is white so the client can tint it (Background.Color), and a
# separate sheen overlay carries the rim and the glossy top half, so the colour can change without losing the style.
#   powershell -File tools/assets/make_bars.ps1
Add-Type -AssemblyName System.Drawing
$out = Join-Path $PSScriptRoot '..\..\src\main\resources\Common\UI\Custom\Hud\Water_of_Arrakis'
$W = 636; $H = 24; $SLANT = 16
$bg = [System.Drawing.Color]::FromArgb(255, 0x34, 0x16, 0x11)
$rim = [System.Drawing.Color]::FromArgb(255, 0x4d, 0x32, 0x2d)

# Is pixel (x, y) inside the shape? left = slanted end on the left, else on the right.
function Inside($x, $y, $left) {
    $cut = [Math]::Round($SLANT * (1 - $y / ($H - 1)))
    if ($left) { return $x -ge $cut }
    return $x -le ($W - 1 - $cut)
}
function Rim($y) { return ($y -le 4 -or $y -ge 20) }

function Make($name, $left, $pixel) {
    $bmp = New-Object System.Drawing.Bitmap $W, $H, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $H; $y++) {
        for ($x = 0; $x -lt $W; $x++) {
            if (Inside $x $y $left) { $bmp.SetPixel($x, $y, (& $pixel $x $y)) }
            else { $bmp.SetPixel($x, $y, [System.Drawing.Color]::Transparent) }
        }
    }
    $bmp.Save((Join-Path $out $name), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

foreach ($left in $true, $false) {
    $side = if ($left) { 'Left' } else { 'Right' }
    Make "Arrakis_Bar_Background_$side@2x.png" $left { param($x, $y) if (Rim $y) { $rim } else { $bg } }
    # White body, a little darker toward the far end so the tint reads as a gradient.
    Make "Arrakis_Bar_Fill_$side@2x.png" $left {
        param($x, $y)
        $t = if ($left) { $x / ($W - 1) } else { 1 - $x / ($W - 1) }
        $v = [int](255 - 40 * $t)
        [System.Drawing.Color]::FromArgb(255, $v, $v, $v)
    }
    # Sheen: bright rim rows, a soft gloss over the top half, a faint shade over the bottom.
    Make "Arrakis_Bar_Sheen_$side@2x.png" $left {
        param($x, $y)
        if (Rim $y) { [System.Drawing.Color]::FromArgb(110, 255, 255, 255) }
        elseif ($y -le 11) { [System.Drawing.Color]::FromArgb(36, 255, 255, 255) }
        else { [System.Drawing.Color]::FromArgb(28, 0, 0, 0) }
    }
}
Get-ChildItem $out -Filter '*.png' | Select-Object Name, Length
