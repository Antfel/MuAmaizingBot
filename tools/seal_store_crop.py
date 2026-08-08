"""Crop MU Coin Store templates from real 1280x720 device captures."""

import sys
from PIL import Image

SRC_STORE = "debug_capture/seal_buy/repro_2_after_empty_tap.png"
SRC_BUY = "debug_capture/seal_buy/repro_3_purchase_window.png"
OUT = "app/src/main/assets/templates/mu/ui/store"


def orange_bbox(img, region, lo=(150, 90, 20), hi=(255, 200, 110)):
    x0, y0, x1, y1 = region
    px = img.load()
    minx, miny, maxx, maxy = 10**9, 10**9, -1, -1
    count = 0
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b = px[x, y]
            if lo[0] <= r <= hi[0] and lo[1] <= g <= hi[1] and lo[2] <= b <= hi[2] and r > b + 60:
                count += 1
                minx = min(minx, x)
                miny = min(miny, y)
                maxx = max(maxx, x)
                maxy = max(maxy, y)
    return (minx, miny, maxx, maxy, count)


def main():
    store = Image.open(SRC_STORE).convert("RGB")
    buy = Image.open(SRC_BUY).convert("RGB")
    print("purchase tight bbox", orange_bbox(buy, (790, 295, 950, 350)))

    crops = {
        "store_open_tab.png": (store, (168, 148, 294, 185)),
        "store_title.png": (store, (596, 100, 688, 130)),
        "random_teleport_seal_item.png": (store, (535, 193, 715, 224)),
        "random_teleport_seal_icon.png": (store, (585, 258, 647, 320)),
    }
    for name, (src, box) in crops.items():
        src.crop(box).save(f"{OUT}/{name}")
        print("saved", name, box, (box[2] - box[0], box[3] - box[1]))


if __name__ == "__main__":
    sys.exit(main())
