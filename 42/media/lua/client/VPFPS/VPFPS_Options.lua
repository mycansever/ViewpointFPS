--[[---------------------------------------------------------------------------
    Viewpoint FPS Optimization - options page
    Build 42 native ModOptions (PZAPI.ModOptions): Options > MODS >
    "Viewpoint FPS Optimization"

    The full control surface lives in the in-game panel (F8); this page
    keeps the essentials and the two keybinds. A diff-sync pushes changes
    to the Java side live.
---------------------------------------------------------------------------]]--

if not PZAPI or not PZAPI.ModOptions then
    print("[VPFPS] PZAPI.ModOptions not available - use the F8 panel or vpfps.properties")
    return
end

local page = PZAPI.ModOptions:create("ViewpointFPS", "Viewpoint FPS Optimization")

page:addDescription("Adaptive performance layer for Project Viewpoint. Press F8 in game for the full settings panel (governor, frame cap, RAM/VRAM), F9 for the overlay.")
page:addDescription("All settings apply live - no restart needed. Requires ZombieBuddy + Project Viewpoint.")
page:addSeparator()

local optEnabled = page:addTickBox("enabled", "Enable optimizations", true,
        "Master kill switch. When off, every Viewpoint setting we touched is restored to stock and the hooks pass through.")

local optCpuAdaptive = page:addTickBox("cpuAdaptive", "CPU quality ladder", true,
        "Lets the governor scale Viewpoint's chunk build / walk / far-job settings under game-thread pressure.")

local optGpuAdaptive = page:addTickBox("gpuAdaptive", "GPU quality ladder", true,
        "Lets the governor scale Viewpoint's draw distance / far detail / corpse settings under GPU pressure.")

local optOverlay = page:addTickBox("overlay", "Performance overlay", true,
        "On-screen readout: fps, 1% low, p99, GPU busy, VRAM, governor state. Toggle with the key below (default F9).")

local optStatsExport = page:addTickBox("statsExport", "Export stats to CSV", false,
        "Appends fps / p90 / p99 / GPU-busy / factor rows to Zomboid\\vpfps-stats.csv every 5 s.")

local optTargetFps = page:addSlider("targetFps", "Target FPS", 30, 240, 5, 60)

local defaultPanelKey = 66 -- LWJGL KEY_F8
pcall(function() defaultPanelKey = Keyboard.KEY_F8 end)
local optPanelKey = page:addKeyBind("panelKey", "Open settings panel", defaultPanelKey)

local defaultOverlayKey = 67 -- LWJGL KEY_F9
pcall(function() defaultOverlayKey = Keyboard.KEY_F9 end)
local optOverlayKey = page:addKeyBind("overlayKey", "Toggle overlay key", defaultOverlayKey)

VPFPS.options = {
    enabled      = optEnabled,
    cpuAdaptive  = optCpuAdaptive,
    gpuAdaptive  = optGpuAdaptive,
    overlay      = optOverlay,
    statsExport  = optStatsExport,
    targetFps    = optTargetFps,
    panelKey     = optPanelKey,
    overlayKey   = optOverlayKey,
}

-- ---------------------------------------------------------------------------
-- diff-sync: read options, push only changes to the Java bridge
-- ---------------------------------------------------------------------------

local last = {}

local function readOpt(o)
    if o == nil then return nil end
    local ok, v = pcall(function() return o:getValue() end)
    if ok then return v end
    return nil
end

local function push()
    local b = VPFPS.getBridge and VPFPS.getBridge() or nil
    if not b then return end

    local function diff(name, value, fn)
        if value ~= nil and value ~= last[name] then
            last[name] = value
            pcall(fn, value)
        end
    end

    diff("enabled",     readOpt(optEnabled),     function(v) b.setEnabled(v) end)
    diff("cpuAdaptive", readOpt(optCpuAdaptive), function(v) b.setCpuAdaptive(v) end)
    diff("gpuAdaptive", readOpt(optGpuAdaptive), function(v) b.setGpuAdaptive(v) end)
    diff("overlay",     readOpt(optOverlay),     function(v) b.setOverlayEnabled(v) end)
    diff("statsExport", readOpt(optStatsExport), function(v) b.setStatsExport(v) end)
    diff("targetFps",   readOpt(optTargetFps),   function(v) b.setTargetFps(v) end)
end

Events.OnGameStart.Add(function()
    last = {}
    push()
end)

local tickAcc = 0
Events.OnTick.Add(function()
    tickAcc = tickAcc + 1
    if tickAcc < 20 then return end -- ~3x per second at 60 fps
    tickAcc = 0
    push()
end)
