#!/usr/bin/env python3
"""Interactive tool to author HUD calibration anchors on a reference capture.

For each anchor (ATK, Switch, Level, Mount):
  1. Drag a rectangle → template crop (use zoom for precision).
  2. Click once OR press «Usar centro» → reference point (red crosshair in-game).

Exports:
  - Template PNGs per anchor
  - calibration_anchors.json
  - calibration_anchors.kt snippet for CalibrationAnchor.kt

Run:
  ./scripts/run_calibration_tool.sh
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

try:
    import tkinter as tk
except ImportError as exc:
    print(
        "tkinter no está disponible en este Python.\n"
        "En macOS con Homebrew instala:\n"
        "  brew install python-tk@3.14\n"
        "Luego vuelve a ejecutar el script.",
        file=sys.stderr,
    )
    raise SystemExit(1) from exc

try:
    from PIL import Image, ImageDraw, ImageTk
except ImportError:
    print("Install Pillow: python3 -m pip install pillow", file=sys.stderr)
    sys.exit(1)

from tkinter import filedialog, messagebox, ttk

REPO_ROOT = Path(__file__).resolve().parents[1]
REF_WIDTH = 2560
REF_HEIGHT = 1440
MIN_ZOOM = 0.25
MAX_ZOOM = 8.0

ANCHOR_DEFS = [
    {
        "id": "atk",
        "label": "ATK (arriba izquierda)",
        "enum": "TOP_LEFT_ATK",
        "panel_at_bottom": True,
        "color": "#00E5FF",
        "point_hint": "centro del texto «ATK»",
    },
    {
        "id": "switch",
        "label": "Switch (arriba derecha)",
        "enum": "TOP_RIGHT_SWITCH",
        "panel_at_bottom": True,
        "color": "#FB8C00",
        "point_hint": "centro del botón Switch",
    },
    {
        "id": "level",
        "label": "Level (abajo izquierda)",
        "enum": "BOTTOM_LEFT_LEVEL",
        "panel_at_bottom": False,
        "color": "#43A047",
        "point_hint": "centro de la palabra «Level» (no el número)",
    },
    {
        "id": "mount",
        "label": "Mount (abajo derecha)",
        "enum": "BOTTOM_RIGHT_MOUNT",
        "panel_at_bottom": False,
        "color": "#E53935",
        "point_hint": "centro del icono circular de montura",
    },
]


@dataclass
class AnchorData:
    id: str
    label: str
    enum: str
    panel_at_bottom: bool
    ref_anchor_x: int
    ref_anchor_y: int
    ref_template_left: int
    ref_template_top: int
    ref_template_width: int
    ref_template_height: int

    @property
    def ref_template_center_x(self) -> int:
        return self.ref_template_left + self.ref_template_width // 2

    @property
    def ref_template_center_y(self) -> int:
        return self.ref_template_top + self.ref_template_height // 2

    @property
    def ref_anchor_offset_x(self) -> int:
        return self.ref_anchor_x - self.ref_template_center_x

    @property
    def ref_anchor_offset_y(self) -> int:
        return self.ref_anchor_y - self.ref_template_center_y

    @property
    def aspect_ratio(self) -> float:
        return self.ref_template_width / max(self.ref_template_height, 1)

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["ref_template_center_x"] = self.ref_template_center_x
        d["ref_template_center_y"] = self.ref_template_center_y
        d["ref_anchor_offset_x"] = self.ref_anchor_offset_x
        d["ref_anchor_offset_y"] = self.ref_anchor_offset_y
        d["aspect_ratio"] = round(self.aspect_ratio, 4)
        return d


class CalibrationAnchorTool:
    def __init__(
        self,
        image_path: Path,
        output_dir: Path,
        copy_assets: bool,
    ) -> None:
        self.image_path = image_path
        self.output_dir = output_dir
        self.copy_assets = copy_assets
        self.output_dir.mkdir(parents=True, exist_ok=True)

        self.source = Image.open(image_path).convert("RGBA")
        if self.source.size != (REF_WIDTH, REF_HEIGHT):
            messagebox.showwarning(
                "Resolución",
                f"La imagen es {self.source.size[0]}×{self.source.size[1]}.\n"
                f"Se esperaba {REF_WIDTH}×{REF_HEIGHT}.\n"
                "Las coordenadas se guardan en píxeles de esta imagen.",
            )

        self.anchors: list[AnchorData | None] = [None] * len(ANCHOR_DEFS)
        self.step = 0
        self.mode = "rect"  # rect | point
        self.drag_start: tuple[int, int] | None = None
        self.pan_start: tuple[float, float, float, float] | None = None
        self.current_rect: tuple[int, int, int, int] | None = None
        self.current_point: tuple[int, int] | None = None
        self.point_prompt_shown = False

        self.fit_scale = 1.0
        self.zoom = 1.0

        self._load_existing_json()

        self.root = tk.Tk()
        self.root.title("MUAmaizingBot — Calibration Anchor Tool")
        self.root.geometry("1320x900")

        self.status_var = tk.StringVar()
        self.step_var = tk.StringVar()
        self.help_var = tk.StringVar()
        self.mode_var = tk.StringVar()
        self.zoom_var = tk.StringVar()

        self._build_ui()
        self._fit_to_window()
        self._refresh_step()
        self._redraw()

    @property
    def view_scale(self) -> float:
        return self.fit_scale * self.zoom

    def _load_existing_json(self) -> None:
        json_path = self.output_dir / "calibration_anchors.json"
        if not json_path.exists():
            return
        try:
            payload = json.loads(json_path.read_text(encoding="utf-8"))
            for i, item in enumerate(payload.get("anchors", [])):
                if i >= len(ANCHOR_DEFS):
                    break
                self.anchors[i] = AnchorData(
                    id=item["id"],
                    label=item["label"],
                    enum=item["enum"],
                    panel_at_bottom=item["panel_at_bottom"],
                    ref_anchor_x=item["ref_anchor_x"],
                    ref_anchor_y=item["ref_anchor_y"],
                    ref_template_left=item["ref_template_left"],
                    ref_template_top=item["ref_template_top"],
                    ref_template_width=item["ref_template_width"],
                    ref_template_height=item["ref_template_height"],
                )
        except (json.JSONDecodeError, KeyError, TypeError):
            pass

    def _build_ui(self) -> None:
        top = ttk.Frame(self.root, padding=8)
        top.pack(fill=tk.X)

        ttk.Label(top, textvariable=self.step_var, font=("", 13, "bold")).pack(anchor=tk.W)
        mode_row = ttk.Frame(top)
        mode_row.pack(fill=tk.X, pady=2)
        ttk.Label(mode_row, textvariable=self.mode_var, font=("", 11, "bold"), foreground="#00ACC1").pack(
            side=tk.LEFT
        )
        ttk.Label(mode_row, textvariable=self.zoom_var, foreground="#78909C").pack(side=tk.RIGHT)
        ttk.Label(top, textvariable=self.help_var, wraplength=1280).pack(anchor=tk.W, pady=4)
        ttk.Label(top, textvariable=self.status_var, foreground="#546E7A").pack(anchor=tk.W)

        btn_row = ttk.Frame(self.root, padding=(8, 0))
        btn_row.pack(fill=tk.X)
        ttk.Button(btn_row, text="◀ Anterior", command=self._prev_step).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_row, text="Siguiente ▶", command=self._next_step).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_row, text="Reiniciar paso", command=self._reset_step).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_row, text="Usar centro del rect.", command=self._use_rect_center).pack(side=tk.LEFT, padx=8)
        ttk.Button(btn_row, text="Guardar todo", command=self._save_all).pack(side=tk.LEFT, padx=8)

        zoom_row = ttk.Frame(self.root, padding=(8, 4))
        zoom_row.pack(fill=tk.X)
        ttk.Label(zoom_row, text="Zoom:").pack(side=tk.LEFT, padx=(0, 4))
        ttk.Button(zoom_row, text="−", width=3, command=lambda: self._zoom_by(1 / 1.25)).pack(side=tk.LEFT)
        ttk.Button(zoom_row, text="+", width=3, command=lambda: self._zoom_by(1.25)).pack(side=tk.LEFT, padx=2)
        ttk.Button(zoom_row, text="100%", command=self._zoom_100).pack(side=tk.LEFT, padx=4)
        ttk.Button(zoom_row, text="200%", command=lambda: self._set_zoom(2.0)).pack(side=tk.LEFT, padx=2)
        ttk.Button(zoom_row, text="Ajustar ventana", command=self._fit_to_window).pack(side=tk.LEFT, padx=4)
        ttk.Label(
            zoom_row,
            text="Rueda del mouse = zoom · Botón derecho arrastrar = mover vista · Espacio+arrastrar = mover",
            foreground="#78909C",
        ).pack(side=tk.LEFT, padx=12)
        ttk.Button(zoom_row, text="Abrir imagen…", command=self._open_image).pack(side=tk.RIGHT)

        canvas_frame = ttk.Frame(self.root, padding=8)
        canvas_frame.pack(fill=tk.BOTH, expand=True)

        self.canvas = tk.Canvas(canvas_frame, bg="#1a1a1a", highlightthickness=0, cursor="crosshair")
        h_scroll = ttk.Scrollbar(canvas_frame, orient=tk.HORIZONTAL, command=self.canvas.xview)
        v_scroll = ttk.Scrollbar(canvas_frame, orient=tk.VERTICAL, command=self.canvas.yview)
        self.canvas.configure(xscrollcommand=h_scroll.set, yscrollcommand=v_scroll.set)
        self.canvas.grid(row=0, column=0, sticky="nsew")
        v_scroll.grid(row=0, column=1, sticky="ns")
        h_scroll.grid(row=1, column=0, sticky="ew")
        canvas_frame.rowconfigure(0, weight=1)
        canvas_frame.columnconfigure(0, weight=1)

        self.canvas_image_id = self.canvas.create_image(0, 0, anchor=tk.NW)

        self.canvas.bind("<ButtonPress-1>", self._on_press)
        self.canvas.bind("<B1-Motion>", self._on_drag)
        self.canvas.bind("<ButtonRelease-1>", self._on_release)
        self.canvas.bind("<ButtonPress-2>", self._on_pan_start)
        self.canvas.bind("<B2-Motion>", self._on_pan_move)
        self.canvas.bind("<ButtonPress-3>", self._on_pan_start)
        self.canvas.bind("<B3-Motion>", self._on_pan_move)
        self.canvas.bind("<MouseWheel>", self._on_wheel)
        self.canvas.bind("<Button-4>", lambda _e: self._zoom_by(1.25))
        self.canvas.bind("<Button-5>", lambda _e: self._zoom_by(1 / 1.25))
        self.root.bind("<space>", self._space_down)
        self.root.bind("<KeyRelease-space>", self._space_up)
        self.root.bind("<Left>", lambda _e: self._prev_step())
        self.root.bind("<Right>", lambda _e: self._next_step())
        self.root.bind("<s>", lambda _e: self._save_all())
        self.root.bind("<r>", lambda _e: self._reset_step())
        self.root.bind("<c>", lambda _e: self._use_rect_center())
        self.space_pan = False

    def _space_down(self, _event: tk.Event) -> None:
        self.space_pan = True
        self.canvas.config(cursor="fleur")

    def _space_up(self, _event: tk.Event) -> None:
        self.space_pan = False
        self.canvas.config(cursor="crosshair")

    def _fit_to_window(self) -> None:
        self.root.update_idletasks()
        cw = max(self.canvas.winfo_width(), 800)
        ch = max(self.canvas.winfo_height(), 500)
        self.fit_scale = min(cw / self.source.width, ch / self.source.height, 1.0)
        self.zoom = 1.0
        self._redraw()
        self.canvas.xview_moveto(0)
        self.canvas.yview_moveto(0)

    def _set_zoom(self, zoom: float) -> None:
        self.zoom = max(MIN_ZOOM, min(MAX_ZOOM, zoom))
        self._redraw()

    def _zoom_100(self) -> None:
        self.fit_scale = 1.0
        self.zoom = 1.0
        self._redraw()

    def _zoom_by(self, factor: float, focus_x: float | None = None, focus_y: float | None = None) -> None:
        old_scale = self.view_scale
        new_zoom = max(MIN_ZOOM, min(MAX_ZOOM, self.zoom * factor))
        if abs(new_zoom - self.zoom) < 1e-6:
            return

        if focus_x is None or focus_y is None:
            focus_x = self.canvas.canvasx(self.canvas.winfo_width() / 2)
            focus_y = self.canvas.canvasy(self.canvas.winfo_height() / 2)

        img_x = focus_x / old_scale
        img_y = focus_y / old_scale

        self.zoom = new_zoom
        self._redraw()

        new_scale = self.view_scale
        new_cx = img_x * new_scale
        new_cy = img_y * new_scale
        self.canvas.xview_moveto(max(0, (new_cx - focus_x) / max(new_scale * self.source.width, 1)))
        self.canvas.yview_moveto(max(0, (new_cy - focus_y) / max(new_scale * self.source.height, 1)))
        self._update_zoom_label()

    def _on_wheel(self, event: tk.Event) -> None:
        delta = getattr(event, "delta", 0)
        if delta == 0:
            return
        factor = 1.25 if delta > 0 else 1 / 1.25
        self._zoom_by(factor, self.canvas.canvasx(event.x), self.canvas.canvasy(event.y))

    def _on_pan_start(self, event: tk.Event) -> None:
        self.pan_start = (event.x, event.y, self.canvas.xview()[0], self.canvas.yview()[0])

    def _on_pan_move(self, event: tk.Event) -> None:
        if self.pan_start is None:
            return
        sx, sy, vx, vy = self.pan_start
        dx = event.x - sx
        dy = event.y - sy
        total_w = max(self.source.width * self.view_scale, 1)
        total_h = max(self.source.height * self.view_scale, 1)
        self.canvas.xview_moveto(max(0, min(1, vx - dx / total_w)))
        self.canvas.yview_moveto(max(0, min(1, vy - dy / total_h)))

    def _to_image_coords(self, event: tk.Event) -> tuple[int, int]:
        cx = self.canvas.canvasx(event.x)
        cy = self.canvas.canvasy(event.y)
        scale = max(self.view_scale, 1e-6)
        x = int(round(cx / scale))
        y = int(round(cy / scale))
        x = max(0, min(self.source.width - 1, x))
        y = max(0, min(self.source.height - 1, y))
        return x, y

    def _on_press(self, event: tk.Event) -> None:
        if self.space_pan or event.num == 2 or event.num == 3:
            self._on_pan_start(event)
            return

        x, y = self._to_image_coords(event)
        if self.mode == "rect":
            self.drag_start = (x, y)
            self.current_rect = (x, y, x, y)
        elif self.mode == "point":
            self.current_point = (x, y)
            self.status_var.set(f"✓ Punto de referencia marcado en ({x}, {y}) — pulsa «Siguiente ▶»")
            self._redraw()

    def _on_drag(self, event: tk.Event) -> None:
        if self.space_pan or event.num in (2, 3) or self.pan_start is not None:
            self._on_pan_move(event)
            return
        if self.mode != "rect" or self.drag_start is None:
            return
        x, y = self._to_image_coords(event)
        x0, y0 = self.drag_start
        left, top = min(x0, x), min(y0, y)
        right, bottom = max(x0, x), max(y0, y)
        self.current_rect = (left, top, right, bottom)
        w, h = right - left, bottom - top
        self.status_var.set(f"Template: ({left},{top})→({right},{bottom}) · {w}×{h}px")
        self._redraw()

    def _on_release(self, event: tk.Event) -> None:
        if self.pan_start is not None:
            self.pan_start = None
            return
        if self.mode != "rect" or self.drag_start is None:
            self.drag_start = None
            return
        self.drag_start = None
        if not self.current_rect:
            return
        left, top, right, bottom = self.current_rect
        if right - left >= 8 and bottom - top >= 8:
            self._enter_point_mode()

    def _enter_point_mode(self) -> None:
        self.mode = "point"
        self._update_help()
        defn = self._current_def()
        if not self.point_prompt_shown:
            self.point_prompt_shown = True
            messagebox.showinfo(
                "Paso 2 — Punto de referencia",
                "Rectángulo del template listo.\n\n"
                "Ahora debes marcar el PUNTO DE REFERENCIA:\n\n"
                f"• Haz UN CLIC en la imagen → {defn['point_hint']}\n"
                "  (aparecerá una cruz blanca)\n\n"
                "O si el centro del rectángulo ya es correcto:\n"
                "• Pulsa «Usar centro del rect.» o la tecla C\n\n"
                "Cuando veas la cruz, pulsa «Siguiente ▶».",
            )
        self.status_var.set(
            f"Paso 2/2: haz UN CLIC en {defn['point_hint']} — o pulsa «Usar centro del rect.»"
        )

    def _use_rect_center(self) -> None:
        if not self.current_rect:
            messagebox.showwarning("Sin rectángulo", "Primero dibuja el rectángulo del template (paso 1).")
            return
        left, top, right, bottom = self.current_rect
        cx = (left + right) // 2
        cy = (top + bottom) // 2
        self.current_point = (cx, cy)
        if self.mode == "rect":
            self.mode = "point"
            self._update_help()
        self.status_var.set(f"✓ Punto = centro del rectángulo ({cx}, {cy}) — pulsa «Siguiente ▶»")
        self._redraw()

    def _current_def(self) -> dict[str, Any]:
        return ANCHOR_DEFS[self.step]

    def _refresh_step(self) -> None:
        defn = self._current_def()
        saved = self.anchors[self.step]
        self.point_prompt_shown = False
        if saved:
            self.current_rect = (
                saved.ref_template_left,
                saved.ref_template_top,
                saved.ref_template_left + saved.ref_template_width,
                saved.ref_template_top + saved.ref_template_height,
            )
            self.current_point = (saved.ref_anchor_x, saved.ref_anchor_y)
            self.mode = "point"
        else:
            self.current_rect = None
            self.current_point = None
            self.mode = "rect"

        self.step_var.set(
            f"Paso {self.step + 1}/{len(ANCHOR_DEFS)} — {defn['label']} ({defn['id']})"
        )
        self._update_help()
        self._update_zoom_label()
        self._redraw()

    def _update_help(self) -> None:
        defn = self._current_def()
        if self.mode == "rect":
            self.mode_var.set("▶ PASO 1/2: Dibujar template (arrastrar rectángulo)")
            self.help_var.set(
                "Arrastra con el botón izquierdo para encerrar el elemento. "
                "Usa zoom (+ / rueda del mouse) para precisión. "
                "Al soltar, pasarás automáticamente al paso 2."
            )
        else:
            self.mode_var.set("▶ PASO 2/2: Marcar punto de referencia (UN CLIC)")
            self.help_var.set(
                f"Haz UN CLIC en la imagen: {defn['point_hint']}. "
                "Si el centro del rectángulo ya es el punto correcto, pulsa «Usar centro del rect.» (tecla C). "
                "Luego «Siguiente ▶»."
            )

    def _update_zoom_label(self) -> None:
        pct = int(round(self.view_scale * 100))
        self.zoom_var.set(f"Zoom: {pct}%")

    def _reset_step(self) -> None:
        self.anchors[self.step] = None
        self.current_rect = None
        self.current_point = None
        self.mode = "rect"
        self.point_prompt_shown = False
        self._update_help()
        self.status_var.set("Paso reiniciado — vuelve a dibujar el rectángulo.")
        self._redraw()

    def _commit_step(self) -> bool:
        if not self.current_rect:
            messagebox.showerror(
                "Falta el template",
                "Paso 1: arrastra un rectángulo sobre el elemento.\n\n"
                "Usa zoom si necesitas precisión.",
            )
            if self.mode == "rect":
                return False
            return False

        left, top, right, bottom = self.current_rect
        w, h = right - left, bottom - top
        if w < 8 or h < 8:
            messagebox.showerror("Rectángulo muy pequeño", "El template debe medir al menos 8×8 px.")
            return False

        if not self.current_point:
            answer = messagebox.askyesno(
                "Falta el punto de referencia",
                "No marcaste el punto de referencia (cruz blanca).\n\n"
                "¿Usar el CENTRO del rectángulo como punto?\n\n"
                "Sí = centro del rectángulo\n"
                "No = cancelar (podrás hacer clic en la imagen)",
            )
            if answer:
                self._use_rect_center()
            else:
                self.mode = "point"
                self._update_help()
                self.status_var.set("Haz UN CLIC en la imagen para marcar el punto de referencia.")
                return False

        assert self.current_point is not None
        defn = self._current_def()
        ax, ay = self.current_point
        self.anchors[self.step] = AnchorData(
            id=defn["id"],
            label=defn["label"],
            enum=defn["enum"],
            panel_at_bottom=defn["panel_at_bottom"],
            ref_anchor_x=ax,
            ref_anchor_y=ay,
            ref_template_left=left,
            ref_template_top=top,
            ref_template_width=w,
            ref_template_height=h,
        )
        return True

    def _prev_step(self) -> None:
        if self.step > 0:
            self.step -= 1
            self._refresh_step()

    def _next_step(self) -> None:
        if not self._commit_step():
            return
        if self.step + 1 < len(ANCHOR_DEFS):
            self.step += 1
            self._refresh_step()
        else:
            self._save_all()

    def _open_image(self) -> None:
        path = filedialog.askopenfilename(
            title="Captura de referencia",
            filetypes=[("PNG", "*.png"), ("All", "*.*")],
        )
        if not path:
            return
        self.image_path = Path(path)
        self.source = Image.open(path).convert("RGBA")
        self._fit_to_window()
        self._reset_step()
        self.status_var.set(f"Imagen cargada: {self.image_path}")

    def _redraw(self) -> None:
        scale = self.view_scale
        vw = max(1, int(round(self.source.width * scale)))
        vh = max(1, int(round(self.source.height * scale)))
        resample = Image.Resampling.NEAREST if scale >= 2.5 else Image.Resampling.BILINEAR
        base = self.source.resize((vw, vh), resample)
        overlay = base.copy()
        draw = ImageDraw.Draw(overlay, "RGBA")

        for i, anchor in enumerate(self.anchors):
            if anchor is None or i == self.step:
                continue
            self._draw_anchor(draw, anchor, ANCHOR_DEFS[i]["color"], alpha=80, scale=scale)

        if self.current_rect:
            color = ANCHOR_DEFS[self.step]["color"]
            self._draw_rect(draw, self.current_rect, color, 160, scale)

        if self.current_point:
            self._draw_cross(draw, self.current_point, "#FFFFFF", 255, scale, size=max(8, int(12 * scale)))

        if self.anchors[self.step]:
            self._draw_anchor(draw, self.anchors[self.step], ANCHOR_DEFS[self.step]["color"], 200, scale)

        self.tk_image = ImageTk.PhotoImage(overlay)
        self.canvas.itemconfig(self.canvas_image_id, image=self.tk_image)
        self.canvas.config(scrollregion=(0, 0, vw, vh))
        self._update_zoom_label()

    def _draw_rect(
        self,
        draw: ImageDraw.ImageDraw,
        rect: tuple[int, int, int, int],
        color: str,
        alpha: int,
        scale: float,
    ) -> None:
        left, top, right, bottom = rect
        box = (left * scale, top * scale, right * scale, bottom * scale)
        r, g, b = int(color[1:3], 16), int(color[3:5], 16), int(color[5:7], 16)
        outline_w = max(1, int(round(2 * scale)))
        draw.rectangle(box, outline=(r, g, b, 255), width=outline_w)
        draw.rectangle(box, fill=(r, g, b, alpha))

    def _draw_cross(
        self,
        draw: ImageDraw.ImageDraw,
        point: tuple[int, int],
        color: str,
        alpha: int,
        scale: float,
        size: int = 12,
    ) -> None:
        x, y = point
        cx, cy = x * scale, y * scale
        r, g, b = int(color[1:3], 16), int(color[3:5], 16), int(color[5:7], 16)
        line = (r, g, b, alpha)
        lw = max(1, int(round(2 * scale)))
        draw.line((cx - size, cy, cx + size, cy), fill=line, width=lw)
        draw.line((cx, cy - size, cx, cy + size), fill=line, width=lw)
        dot = max(2, int(round(3 * scale)))
        draw.ellipse((cx - dot, cy - dot, cx + dot, cy + dot), fill=(255, 82, 82, 255))

    def _draw_anchor(
        self,
        draw: ImageDraw.ImageDraw,
        anchor: AnchorData,
        color: str,
        alpha: int,
        scale: float,
    ) -> None:
        rect = (
            anchor.ref_template_left,
            anchor.ref_template_top,
            anchor.ref_template_left + anchor.ref_template_width,
            anchor.ref_template_top + anchor.ref_template_height,
        )
        self._draw_rect(draw, rect, color, alpha, scale)
        self._draw_cross(
            draw,
            (anchor.ref_anchor_x, anchor.ref_anchor_y),
            "#FFFFFF",
            min(255, alpha + 40),
            scale,
        )

    def _save_all(self) -> None:
        if not all(self.anchors):
            missing = [ANCHOR_DEFS[i]["id"] for i, a in enumerate(self.anchors) if a is None]
            messagebox.showerror(
                "Incomplete",
                f"Faltan anchors: {', '.join(missing)}\nCompleta los 4 pasos antes de guardar.",
            )
            return

        templates_dir = self.output_dir / "templates"
        templates_dir.mkdir(exist_ok=True)

        for anchor in self.anchors:
            assert anchor is not None
            crop = self.source.crop(
                (
                    anchor.ref_template_left,
                    anchor.ref_template_top,
                    anchor.ref_template_left + anchor.ref_template_width,
                    anchor.ref_template_top + anchor.ref_template_height,
                )
            )
            crop.save(templates_dir / f"{anchor.id}.png")

        payload = {
            "reference_width": self.source.width,
            "reference_height": self.source.height,
            "source_image": str(self.image_path),
            "anchors": [a.to_dict() for a in self.anchors if a],
        }
        json_path = self.output_dir / "calibration_anchors.json"
        json_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

        kt_path = self.output_dir / "calibration_anchors.kt"
        kt_path.write_text(self._kotlin_snippet(), encoding="utf-8")

        assets_dir = (
            REPO_ROOT
            / "app"
            / "src"
            / "main"
            / "assets"
            / "templates"
            / f"{REF_WIDTH}x{REF_HEIGHT}"
            / "mu"
            / "calibration"
        )
        if self.copy_assets:
            assets_dir.mkdir(parents=True, exist_ok=True)
            for anchor in self.anchors:
                assert anchor is not None
                src = templates_dir / f"{anchor.id}.png"
                src.copy(assets_dir / f"{anchor.id}.png")

        msg = (
            f"Guardado en:\n{self.output_dir}\n\n"
            f"- templates/*.png\n"
            f"- calibration_anchors.json\n"
            f"- calibration_anchors.kt"
        )
        if self.copy_assets:
            msg += f"\n\nTemplates copiados a:\n{assets_dir}"
        messagebox.showinfo("Guardado", msg)
        self.status_var.set(f"Exportado → {self.output_dir}")

    def _kotlin_snippet(self) -> str:
        lines = [
            "// Generated by scripts/calibration_anchor_tool.py",
            f"// Source: {self.image_path.name} ({self.source.width}×{self.source.height})",
            "// Paste enum entries into CalibrationAnchor.kt",
            "",
        ]
        for anchor in self.anchors:
            assert anchor is not None
            panel = "true" if anchor.panel_at_bottom else "false"
            lines.extend(
                [
                    f"    {anchor.enum}(",
                    f'        id = "{anchor.id}",',
                    f'        label = "{anchor.label}",',
                    f'        templateAssetPath = "templates/{REF_WIDTH}x{REF_HEIGHT}/mu/calibration/{anchor.id}.png",',
                    f"        refAnchorX = {anchor.ref_anchor_x},",
                    f"        refAnchorY = {anchor.ref_anchor_y},",
                    f"        refTemplateLeft = {anchor.ref_template_left},",
                    f"        refTemplateTop = {anchor.ref_template_top},",
                    f"        refTemplateWidth = {anchor.ref_template_width},",
                    f"        refTemplateHeight = {anchor.ref_template_height},",
                    f"        panelAtBottom = {panel},",
                    "    ),",
                    "",
                ]
            )
        return "\n".join(lines)

    def run(self) -> None:
        self.root.mainloop()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Author HUD calibration anchors on a reference capture.")
    parser.add_argument(
        "--image",
        type=Path,
        default=REPO_ROOT / "debug_capture" / "ref_full.png",
        help="Reference screenshot (default: debug_capture/ref_full.png)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=REPO_ROOT / "debug_capture" / "calibration_output",
        help="Output directory for templates + JSON + Kotlin snippet",
    )
    parser.add_argument(
        "--copy-assets",
        action="store_true",
        help="Also copy template PNGs into app/src/main/assets/templates/2560x1440/mu/calibration/",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.image.exists():
        print(f"Image not found: {args.image}", file=sys.stderr)
        print("Capture one with: adb exec-out screencap -p > debug_capture/ref_full.png", file=sys.stderr)
        sys.exit(1)
    CalibrationAnchorTool(args.image, args.output, args.copy_assets).run()


if __name__ == "__main__":
    main()
