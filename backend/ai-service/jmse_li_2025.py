"""
Progressive color correction + vision-inspired adaptive enhancement
from Li et al., J. Mar. Sci. Eng. 2025, 13(9), 1820
https://doi.org/10.3390/jmse13091820

Implements Section 3.1 (Eq. 1–8), Section 3.2 / Algorithm 1 (Eq. 9–21).
GC base-detail separation uses Gaussian curvature filtering [57] (Gong & Sbalzarini, TIP 2017)
via gong_curvature_filter.gc_filter_gray.

Assumptions where the paper leaves constants unspecified (MATLAB code not public):
- Guided filter window radius r=8 (Eq. 7–8; epsilon = 1e-4 as in the paper).
- Illumination refinement: window Omega for Eq. (14) is 15×15; zeta = 1e-4.
- Balancing parameter tau in Eq. (13)/(15): tau = 1.0 (not stated numerically in the article text).
- Gaussian G in Eq. (19): sigma_c = 2.0 pixels, kernel 11×11, discrete normalization sum=1.
- GC filter iterations: 10 per channel (default in Gong Python interface; couples with lambda_c in Eq. 10).
- Eq. (17): juxtaposition I_base * gamma (multiplication) for physically consistent shadow gain;
  HTML omits an explicit fraction bar between I_base and gamma.
"""
from __future__ import annotations

import cv2
import numpy as np
from scipy import optimize
from scipy import sparse
from scipy.sparse.linalg import cg

from gong_curvature_filter import gc_filter_gray


ALPHA_GLOBAL = 0.05  # Eq. (6), Section 3.1.1
EPS_GUIDED = 1e-4  # below Eq. (8)
KAPPA_DETAIL = 1.2  # Eq. (20), Section 4.1
OMEGA_D = 3.0  # Eq. (21)
ZETA_ILLUM = 1e-4
TAU_ILLUM = 1.0
OMEGA_ILLUM_R = 7  # half-window -> (2r+1)=15


def _rayleigh_pdf(i: np.ndarray, mu: float, s: float) -> np.ndarray:
    """Eq. (3): f(i; mu, s) = (i-mu)/s^2 * exp(-(i-mu)^2/(2s^2))."""
    s = max(float(s), 1e-6)
    z = (i.astype(np.float64) - mu) / s
    out = (z / s) * np.exp(-(z * z) / 2.0)
    return np.maximum(out, 0.0)


def _fit_rayleigh_channel(pdf: np.ndarray) -> tuple[float, float]:
    """Eq. (2): least-squares fit of Eq. (3) to pdf_c(i)."""
    i = np.arange(256, dtype=np.float64)
    w = np.sqrt(np.maximum(pdf, 1e-12))

    def residual(theta: np.ndarray) -> np.ndarray:
        mu, ls = float(theta[0]), float(theta[1])
        s = np.exp(ls)
        f = _rayleigh_pdf(i, mu, s)
        return (pdf - f) * w

    # Reasonable bounds: mu in [0, 240], s in ~[0.5, 80]
    best = (0.0, 20.0)
    best_cost = 1e18
    for mu0 in (0.0, 30.0, 60.0, 90.0):
        for s0 in (5.0, 15.0, 25.0, 40.0):
            try:
                res = optimize.least_squares(
                    residual,
                    x0=np.array([mu0, np.log(s0)], dtype=np.float64),
                    bounds=([0.0, np.log(0.5)], [254.0, np.log(120.0)]),
                    max_nfev=80,
                    ftol=1e-9,
                )
                if res.cost < best_cost:
                    best_cost = res.cost
                    best = (float(res.x[0]), float(np.exp(res.x[1])))
            except Exception:
                continue
    return best


def _guided_filter(guide: np.ndarray, src: np.ndarray, radius: int, eps: float) -> np.ndarray:
    """He et al. guided filter (implements Eq. 7–8 structure). guide, src: float32, same shape."""
    g = guide.astype(np.float64)
    p = src.astype(np.float64)
    k = 2 * radius + 1
    mean_g = cv2.boxFilter(g, -1, (k, k), normalize=True)
    mean_p = cv2.boxFilter(p, -1, (k, k), normalize=True)
    corr_g = cv2.boxFilter(g * g, -1, (k, k), normalize=True)
    corr_gp = cv2.boxFilter(g * p, -1, (k, k), normalize=True)
    var_g = corr_g - mean_g * mean_g
    cov_gp = corr_gp - mean_g * mean_p
    a = cov_gp / (var_g + eps)
    b = mean_p - a * mean_g
    mean_a = cv2.boxFilter(a, -1, (k, k), normalize=True)
    mean_b = cv2.boxFilter(b, -1, (k, k), normalize=True)
    return (mean_a * g + mean_b).astype(np.float32)


def _progressive_color_correction(bgr_u8: np.ndarray) -> np.ndarray:
    """Section 3.1: global Rayleigh-based mapping + HE + guided red refinement."""
    rgb = cv2.cvtColor(bgr_u8, cv2.COLOR_BGR2RGB)
    I = rgb.astype(np.float32) / 255.0  # Eq. (5) uses I_c in [0,1] with *255 in paper
    mus = []
    ss = []
    for c in range(3):
        hist = cv2.calcHist([rgb], [c], None, [256], [0, 256]).ravel().astype(np.float64)
        total = max(float(hist.sum()), 1.0)
        pdf = hist / total  # Eq. (1)
        mu, s = _fit_rayleigh_channel(pdf)
        mus.append(mu)
        ss.append(s)

    mu_r, mu_g, mu_b = mus
    s_r, s_g, s_b = ss
    D = max(mu_r, mu_g, mu_b)  # Eq. (4)
    if D < 1e-6:
        D = 1.0
    smax = max(s_r, s_g, s_b)
    if smax < 1e-6:
        smax = 1.0

    betas = []
    for sc in (s_r, s_g, s_b):
        betas.append((sc / smax) ** ALPHA_GLOBAL)  # Eq. (6)

    # Eq. (5): I_p^c = beta * (mu_c/D) * I_c * 255
    Ip = np.empty_like(I, dtype=np.float32)
    for c, beta, mu_c in zip(range(3), betas, (mu_r, mu_g, mu_b)):
        Ip[:, :, c] = beta * (mu_c / D) * I[:, :, c] * 255.0
    Ip = np.clip(Ip, 0.0, 255.0).astype(np.uint8)

    # "Additional equalization" (Section 3.1.1): apply to each I_p^c
    Ip_eq = np.zeros_like(Ip)
    for c in range(3):
        Ip_eq[:, :, c] = cv2.equalizeHist(Ip[:, :, c])

    Ipg = Ip_eq[:, :, 1].astype(np.float32)
    Ipr = Ip_eq[:, :, 0].astype(np.float32)
    I_lc_r = _guided_filter(Ipg, Ipr, radius=8, eps=EPS_GUIDED)  # Eq. 7–8
    out = Ip_eq.copy()
    out[:, :, 0] = np.clip(I_lc_r, 0, 255).astype(np.uint8)
    return cv2.cvtColor(out, cv2.COLOR_RGB2BGR)


def _pad_for_gc(chan: np.ndarray, pad: int = 4) -> tuple[np.ndarray, tuple[int, int]]:
    h, w = chan.shape[:2]
    padded = cv2.copyMakeBorder(chan, pad, pad, pad, pad, cv2.BORDER_REFLECT101)
    return padded, (h, w)


def _gc_base_layer(chan_u8: np.ndarray, iterations: int = 10) -> np.ndarray:
    """Eq. (10)–(11): GC filter [57] on channel, return base layer same size as input."""
    pad = 4
    p, (h, w) = _pad_for_gc(chan_u8, pad)
    fl = p.astype(np.float32)
    base_p = gc_filter_gray(fl, total_iter=iterations, dtype=np.float32)
    base = base_p[pad : pad + h, pad : pad + w]
    return np.clip(base, 0.0, 255.0)


def _illumination_map_t(T_hat: np.ndarray, tau: float = TAU_ILLUM) -> np.ndarray:
    """
    Eq. (12)–(15): refine illumination T from T_hat (single channel [0,1]).
    Uses weighted Laplacian from Eq. (13) with W_d from Eq. (14).
    """
    h, w = T_hat.shape
    n = h * w

    gx = T_hat[:, 1:] - T_hat[:, :-1]  # forward h at (y,x): x=0..w-2
    gy = T_hat[1:, :] - T_hat[:-1, :]  # forward v at (y,x): y=0..h-2

    # Eq. (14): W_d at each pixel (sum over Omega of nabla_d T_hat)
    gx_pad = np.pad(gx, ((0, 0), (0, 1)), mode="edge")  # extend for window sums -> align HxW
    gy_pad = np.pad(gy, ((0, 1), (0, 0)), mode="edge")

    wh = np.zeros((h, w), dtype=np.float64)
    wv = np.zeros((h, w), dtype=np.float64)
    ksz = 2 * OMEGA_ILLUM_R + 1
    sum_h = cv2.boxFilter(gx_pad.astype(np.float64), -1, (ksz, ksz), normalize=False)
    sum_v = cv2.boxFilter(gy_pad.astype(np.float64), -1, (ksz, ksz), normalize=False)
    wh[:, :] = 1.0 / (np.abs(sum_h) + ZETA_ILLUM)
    wv[:, :] = 1.0 / (np.abs(sum_v) + ZETA_ILLUM)

    # Eq. (13) quadratic weights: tau * W_d * (nabla_d T)^2 / (|nabla_d T_hat| + zeta)
    eps_g = ZETA_ILLUM
    row: list[int] = []
    col: list[int] = []
    data: list[float] = []

    def add(i: int, j: int, v: float) -> None:
        row.append(i)
        col.append(j)
        data.append(v)

    # A = I + L; L is graph Laplacian from Eq. (13)–(14)
    for i in range(n):
        add(i, i, 1.0)

    # Horizontal edges between (y,x) and (y,x+1), x=0..w-2
    for y in range(h):
        for x in range(w - 1):
            wx = tau * wh[y, x] / (abs(float(gx[y, x])) + eps_g)
            i = y * w + x
            j = y * w + x + 1
            add(i, i, wx)
            add(j, j, wx)
            add(i, j, -wx)
            add(j, i, -wx)

    # Vertical edges between (y,x) and (y+1,x), y=0..h-2
    for y in range(h - 1):
        for x in range(w):
            wy = tau * wv[y, x] / (abs(float(gy[y, x])) + eps_g)
            i = y * w + x
            j = (y + 1) * w + x
            add(i, i, wy)
            add(j, j, wy)
            add(i, j, -wy)
            add(j, i, -wy)

    A = sparse.coo_matrix((data, (row, col)), shape=(n, n)).tocsr()
    b = T_hat.reshape(-1).astype(np.float64)
    # CG for large H*W (direct solve is prohibitive for multi‑megapixel frames).
    maxiter = min(2000, max(200, n // 500))
    t, info = cg(A, b, rtol=1e-7, maxiter=maxiter)
    if info != 0:
        t, _ = cg(A, b, rtol=1e-5, maxiter=maxiter * 2)
    return np.clip(t.reshape(h, w), 0.0, 1.0)


def _gaussian_kernel_2d(sigma: float, k: int) -> np.ndarray:
    ax = np.arange(k) - (k - 1) / 2.0
    xx, yy = np.meshgrid(ax, ax)
    g = np.exp(-(xx * xx + yy * yy) / (2.0 * sigma * sigma))
    g /= g.sum()
    return g.astype(np.float32)


def enhance_jmse_li_2025(bgr_u8: np.ndarray) -> np.ndarray:
    """
    Full pipeline: Section 3.1 color correction, then Algorithm 1 (vision module).
    Input/output BGR uint8.
    """
    work = _progressive_color_correction(bgr_u8)
    rgb = cv2.cvtColor(work, cv2.COLOR_BGR2RGB).astype(np.float32)

    bases = []
    details = []
    for c in range(3):
        ch = rgb[:, :, c]
        base = _gc_base_layer(ch.astype(np.uint8), iterations=10)  # Eq. 10–11
        det = ch - base  # Eq. (11)
        bases.append(base)
        details.append(det)
    base_stack = np.stack(bases, axis=2)
    det_stack = np.stack(details, axis=2)

    # Eq. (12): min over channels of base, then normalize illumination to [0,1]
    T_hat = np.min(base_stack, axis=2)
    T_hat = T_hat / 255.0
    T_hat = np.clip(T_hat, 0.0, 1.0)
    T_map = _illumination_map_t(T_hat)  # Eq. (13)–(15)

    # Eq. (16)–(17)
    gamma = np.power(2.0, 0.5 - T_map).astype(np.float32)
    O_base = base_stack * gamma[..., None]

    # Eq. (18)–(21)
    Gk = _gaussian_kernel_2d(2.0, 11)
    O_detail = np.zeros_like(det_stack, dtype=np.float32)
    for c in range(3):
        d = det_stack[:, :, c]
        w = cv2.filter2D(np.abs(d), -1, Gk, borderType=cv2.BORDER_REFLECT101)
        w = np.maximum(w, 1e-8)
        O_detail[:, :, c] = d * np.power(w, KAPPA_DETAIL)

    O = O_base + OMEGA_D * O_detail
    O = np.clip(O, 0.0, 255.0).astype(np.uint8)
    return cv2.cvtColor(O, cv2.COLOR_RGB2BGR)
