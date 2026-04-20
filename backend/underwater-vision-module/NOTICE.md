# Third-party algorithm

The underwater color correction implemented in `src/uvm/pipeline/nikolaj_bech_color_correction.py` is a **Python port** of the algorithm described in the JavaScript package **underwater-image-color-correction** by Nikolaj Bech Andersen:

- Repository: https://github.com/nikolajbech/underwater-image-color-correction  
- Upstream entry point: `getColorFilterMatrix(pixels, width, height)` in `index.js`

Before commercial redistribution, **verify the license** in the upstream repository and comply with its terms. This project’s port follows the published logic for interoperability; it is not a verbatim copy of the upstream repository beyond the algorithmic behavior.
