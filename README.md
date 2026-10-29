# BattText — Plain-Text Android Battery Telemetry Monitor

**BattText** is a zero-permission-bloat, text-only Android battery monitor modeled on **BatteryGuru** and styled after **[plaintextsports.com](https://plaintextsports.com)**. Every screen, box, progress bar, sparkline graph, table, tab, toggle, stepper, and modal dialog is rendered purely out of text and Unicode box-drawing / ASCII characters.

## Features

- **100% Plain-Text Scoreboard UI**:
  - No icons, elevation shadows, cards, gradients, ripples, or animations.
  - Defaults to the device's **System Font (`FontFamily.Default`)** with a toggle for **System Monospace (`FontFamily.Monospace`)** in Settings.
  - Proportional-safe box-drawing frames (`┌─ TITLE ─┐`) and weighted table/graph columns that never drift or jagged-wrap under variable-width system fonts.
  - Optional **Pure ASCII Borders** fallback (`+-|=#`) for retro terminal purists.
  - **Dracula** (dark) and **Alucard** (light) semantic color palettes with `Auto / Dark / Light` override and matching system status/navigation bars.
- **Live Battery Telemetry (Dashboard)**:
  - Battery `%` with block progress bar `[███████▌░░]`, charging status, and plug type (`AC / USB / WLS`).
  - Normalized real-time electric current (`mA`) and rolling average (`AVG mA`) handling device-specific `µA` vs `mA` and sign conventions automatically.
  - Live power (`W`), voltage (`V` / `mV`), temperature (`°C` / `°F`), health, and chemistry.
  - Charge / discharge speed (`%/hr`) and rolling-average **Time to Full / Time to Empty** (`0h 42m`).
  - Hardware `CHARGE_COUNTER` (`mAh`) and estimated full capacity vs configurable design capacity.
- **Deep Sleep, Doze & Screen Drain Accounting**:
  - Live **Doze State** (`DOZING (IDLE)` vs `LIGHT IDLE (SCR OFF)` vs `ACTIVE (SCR ON)`) and Power Saver status.
  - System-wide **Boot Deep Sleep %** and **Boot Awake %** (`SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()`).
  - Tracked **Screen-On vs Screen-Off Drain** (`%` and `%/hr`) plus **Screen-Off Deep Sleep vs Wakelock Awake Idle** breakdown.
- **History & Sessions**:
  - Configurable periodic sampling (`1–60 min`) to **Room** with automatic old-data pruning.
  - `24H` and `7D` block-character graphs (`▁▂▃▄▅▆▇█`) for Battery `%` and Temperature.
  - Automatic charge and discharge session tracking (`start/end time`, `start->end %`, `avg mA`, `peak temp`).
- **Text-Only Notifications & Threshold Alerts**:
  - API 34 `specialUse` foreground service with zero wake locks and zero background polling.
  - Persistent silent ongoing notification (`78% | Charging AC | +1850mA | 31.2C | 4.21V | full in 0h42m`) with `BigTextStyle` expanded telemetry block.
  - Dynamic **percentage-digits status bar icon** rendered on the fly so even the status bar icon is pure text.
  - One-shot alerts with `DISMISS` action for **Charge Limit (80%)**, **Full Charge (100%)**, **Low (20%)**, **Critical (10%)**, **High Temp (40°C)**, and optional **Plug/Unplug** events.

## Screenshots Placeholder

```text
BATTTEXT                                14:32:07
════════════════════════════════════════════════
[*DASH*]   HISTORY   SESSIONS   SETTINGS
────────────────────────────────────────────────
┌─ BATTERY ──────────────────────────── LI-ION ─┐
│ LEVEL        78%  [█████████▌░░]              │
│ STATUS       CHARGING (AC)                    │
│ CURRENT      +1850 mA  (AVG +1820 mA)         │
│ POWER        7.79 W                           │
│ VOLTAGE      4.210 V (4210 mV)                │
│ TEMP         31.2 C                           │
│ HEALTH       GOOD                             │
└───────────────────────────────────────────────┘
```
