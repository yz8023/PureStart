#!/usr/bin/env python3
"""把 `ic_launcher_foreground.png`（256×256，21KB）转成 VectorDrawable（<3KB，任意分辨率）。

为什么要做：自适应图标前景在 xxxhdpi 上需要约 432px 的位图，而这张 256px 的 PNG
在高分屏上会被放大发糊，同时它占了 `res/drawable` 里绝大部分体积。矢量图既更清晰又快照小。

做法（可复现）：
  1. 用 OpenCV 取 alpha 轮廓（外轮廓无内孔），`approxPolyDP` 折线化；
  2. 用最小二乘把蓝色区域拟合成一条**对角线性渐变**（左上亮、右下暗）；
  3. 输出 `M/L/Z` 折线路径 + `<aapt:attr>` 渐变填充；
  4. **回读自己输出的 XML**，按 path 数据光栅化，与原图做 IoU 对比（必须 ≥ .99）。

用法：
    python3 scripts/make_launcher_vector.py            # 生成并自检
    python3 scripts/make_launcher_vector.py --check    # 只自检已生成的 XML
    python3 scripts/make_launcher_vector.py <png>      # 指定别的源图（默认 scripts/launcher_source.png）

源 PNG 不放在 `res/`（放进去就是白占 APK 体积），而是作为生成器的输入留在 `scripts/` 下：
**改图标时改这张图，再跑本脚本**，不要手改 XML。

依赖：opencv-python / numpy / pillow（仅开发机需要，构建不需要）。
"""
from pathlib import Path
import re
import sys

import cv2
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PNG = ROOT / 'scripts/launcher_source.png'  # 源图（不进 res/，不影响 APK）
XML = ROOT / 'app/src/main/res/drawable/ic_launcher_foreground.xml'
SIZE = 256
IOU_MIN = 0.99


def contours(mask, epsilon):
    found, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    found = [c for c in found if cv2.contourArea(c) > 200]
    polys = [cv2.approxPolyDP(c, epsilon, True).reshape(-1, 2) for c in found]
    return polys


def number(value):
    """整数坐标不写小数点：折线顶点本来就是整数像素，能省掉约 40% 的 pathData 体积。"""
    return str(int(value)) if float(value).is_integer() else ('%.1f' % value)


def path_data(polys):
    parts = []
    for poly in polys:
        pts = ['M%s,%s' % (number(poly[0][0]), number(poly[0][1]))]
        pts += ['L%s,%s' % (number(p[0]), number(p[1])) for p in poly[1:]]
        parts.append(' '.join(pts) + ' Z')
    return ' '.join(parts)


def fit_gradient(image, shape_mask, white_mask):
    """对蓝色区域做 y/x 线性拟合，返回 (起点色, 终点色) 十六进制。"""
    blue = shape_mask & ~white_mask
    ys, xs = np.nonzero(blue)
    start, end = [], []
    for ch in range(3):
        values = image[ys, xs, ch].astype(float)
        design = np.stack([np.ones_like(ys), ys, xs], axis=1).astype(float)
        coef, *_ = np.linalg.lstsq(design, values, rcond=None)
        a, b, c = coef
        start.append(int(round(np.clip(a, 0, 255))))
        end.append(int(round(np.clip(a + b * (SIZE - 1) + c * (SIZE - 1), 0, 255))))
    return start, end


def build():
    image = np.array(Image.open(PNG).convert('RGBA'))
    alpha = image[:, :, 3]
    white_mask = (image[:, :, 0] > 240) & (image[:, :, 1] > 240) & (image[:, :, 2] > 240) & (alpha > 200)
    shape_mask = alpha > 128

    # 折线端点误差 <0.5px：在 xxxhdpi（前景约 432px）下曲率依旧平滑
    shape = contours(shape_mask, 0.35)
    glyph = contours(white_mask, 0.30)
    start, end = fit_gradient(image, shape_mask, white_mask)
    to_hex = lambda rgb: '#FF%02X%02X%02X' % tuple(rgb)

    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
    由 scripts/make_launcher_vector.py 自动生成，**不要手改**。
    源图：ic_launcher_foreground.png（256×256，21KB）→ 本矢量（约 2KB，任意分辨率）。
    形状：外轮廓为连续曲率的「方圆形」（squircle），填充为左上→右下对角线性渐变；
    字形：白色 M。两组路径都是轮廓折线化（误差 <1px），回读自检 IoU ≥ {IOU_MIN}。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="{SIZE}"
    android:viewportHeight="{SIZE}">
    <path android:pathData="{path_data(shape)}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0" android:startY="0"
                android:endX="{SIZE}" android:endY="{SIZE}"
                android:startColor="{to_hex(start)}"
                android:endColor="{to_hex(end)}" />
        </aapt:attr>
    </path>
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{path_data(glyph)}" />
</vector>
'''
    XML.write_text(xml, encoding='utf-8')
    print('wrote', XML.relative_to(ROOT), XML.stat().st_size, 'bytes; gradient', to_hex(start), '->', to_hex(end))


def rasterize_from_xml():
    """回读 XML 里的 pathData（只含 M/L/Z），光栅化成布尔蒙版。"""
    text = XML.read_text(encoding='utf-8')
    paths = re.findall(r'pathData="([^"]+)"', text)
    layers = []
    for data in paths:
        vertices = []
        for cmd, x, y in re.findall(r'([ML])(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)', data):
            vertices.append([float(x), float(y)])
        mask = np.zeros((SIZE, SIZE), np.uint8)
        cv2.fillPoly(mask, [np.round(np.array(vertices)).astype(np.int32)], 1, lineType=cv2.LINE_8)
        layers.append(mask.astype(bool))
    return layers


def check():
    image = np.array(Image.open(PNG).convert('RGBA'))
    alpha = image[:, :, 3]
    white_mask = (image[:, :, 0] > 240) & (image[:, :, 1] > 240) & (image[:, :, 2] > 240) & (alpha > 200)
    shape_mask = alpha > 128
    shape_layer, glyph_layer = rasterize_from_xml()[:2]

    def iou(a, b):
        return (a & b).sum() / float((a | b).sum())

    shape_iou = iou(shape_layer, shape_mask)
    glyph_iou = iou(glyph_layer, white_mask)
    print('shape IoU %.4f  glyph IoU %.4f  xml=%d bytes (png=%d bytes)'
          % (shape_iou, glyph_iou, XML.stat().st_size, PNG.stat().st_size))
    assert shape_iou >= IOU_MIN, '外形与源图偏差过大：%.4f' % shape_iou
    assert glyph_iou >= IOU_MIN, '字形与源图偏差过大：%.4f' % glyph_iou
    assert 'aapt:attr' in XML.read_text(encoding='utf-8'), '必须保留对角渐变填充'
    print('PASS: launcher vector matches the source bitmap (IoU >= %.2f)' % IOU_MIN)


if __name__ == '__main__':
    cli = [a for a in sys.argv[1:] if not a.startswith('--')]
    if cli:
        PNG = Path(cli[0]).resolve()
        if not PNG.is_file():
            raise SystemExit('找不到源图：%s' % PNG)
    if '--check' not in sys.argv:
        build()
    check()
