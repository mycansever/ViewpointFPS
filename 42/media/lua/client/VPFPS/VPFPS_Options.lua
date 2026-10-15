--[[---------------------------------------------------------------------------
    Viewpoint FPS Optimization - options page
    Build 42 native ModOptions (PZAPI.ModOptions): Options > MODS >
    "Viewpoint FPS Optimization"

    Every setting is pushed live to the Java side (no restart); the Java
    side persists to <user>\Zomboid\vpfps.properties. A diff-sync runs on
    a slow tick so changes apply even where no change callback exists.
---------------------------------------------------------------------------]]--

if not PZAPI or not PZAPI.ModOptions then
    print("[VPFPS] PZAPI.ModOptions not available - configure via vpfps.properties")
    return
end

local page = PZAPI.ModOptions:create("ViewpointFPS", "Viewpoint FPS Optimization")

page:addDescription("Adaptive performance layer for Project Viewpoint (first/third-person renderer).")
page:addDescription("All settings apply live - no restart needed. Requires ZombieBuddy + Project Viewpoint.")
page:addSeparator()

local optEnabled = page:addTickBox("enabled", "Enable optimizations", true,
        "Master kill switch. When off, every hook passes through and Viewpoint runs untouched.")

local optAdaptive = page:addTickBox("adaptive", "Adaptive quality governor", true,
        "Automatically lowers/raises render scale, shadows and the mesh budget to hold the target frame time. When off, the fixed values below are used.")

local optDynamicRes = page:addTickBox("dynamicResolution", "Dynamic resolution", true,
        "Let the governor scale Viewpoint's internal render resolution (only if this Viewpoint build exposes the knob).")

local optMeshBudget = page:addTickBox("meshBudgetEnabled", "Chunk mesh budget", true,
        "Caps the time Viewpoint may spend rebuilding chunk meshes per frame; leftover chunks rebuild next frame. Smooths streaming spikes when driving or turning fast.")

local optWalkThrottle = page:addTickBox("chunkWalkThrottle", "Chunk-walk idle throttle", true,
        "While you stand still (looting, fighting, crafting) Viewpoint still re-scans its chunks every frame. This limits that scan to a few times per second when idle; full rate returns the instant you move.")

local optDrawDist = page:addTickBox("drawDistanceAdaptive", "Adaptive draw distance", true,
        "Lets the governor scale Viewpoint's chunk/draw distance down (and back up) under game-thread pressure. Fewer chunks = fewer meshes and draws.")

local optStatsExport = page:addTickBox("statsExport", "Export stats to CSV", false,
        "Appends fps / frame-time / p90 / p99 / render-share rows to Zomboid\\vpfps-stats.csv every 5 s - a lightweight benchmark log of your session.")

local optOverlay = page:addTickBox("overlay", "Performance overlay", true,
        "On-screen readout: fps, frame time, p99, VP render share, governor state. Toggle in game with the key below (default F9).")

local optTargetFps = page:addSlider("targetFps", "Target FPS", 30, 240, 5, 60)
local optScaleFixed = page:addSlider("renderScaleFixed", "Fixed render scale", 0.5, 1.0, 0.05, 1.0)
local optScaleMin = page:addSlider("renderScaleMin", "Min render scale (adaptive floor)", 0.4, 1.0, 0.05, 0.5)
local optMeshMs = page:addSlider("meshBudgetMs", "Mesh budget (ms per frame)", 0.5, 8.0, 0.5, 2.0)
local optWalkMs = page:addSlider("chunkWalkThrottleMs", "Idle chunk-walk interval (ms)", 100, 1000, 50, 250)
local optDrawDistMin = page:addSlider("drawDistanceMin", "Min draw distance (factor)", 0.5, 1.0, 0.05, 0.7)

local defaultKey = 67 -- LWJGL KEY_F9
pcall(function() defaultKey = Keyboard.KEY_F9 end)
local optKey = page:addKeyBind("overlayKey", "Toggle overlay key", defaultKey)

-- expose the option objects to the bootstrap (keybind handler)
VPFPS.options = {
    enabled           = optEnabled,
    adaptive          = optAdaptive,
    dynamicResolution = optDynamicRes,
    meshBudgetEnabled = optMeshBudget,
    chunkWalkThrottle = optWalkThrottle,
    drawDistanceAdaptive = optDrawDist,
    statsExport       = optStatsExport,
    overlay           = optOverlay,
    targetFps         = optTargetFps,
    renderScaleFixed  = optScaleFixed,
    renderScaleMin    = optScaleMin,
    meshBudgetMs      = optMeshMs,
    chunkWalkThrottleMs = optWalkMs,
    drawDistanceMin   = optDrawDistMin,
    overlayKey        = optKey,
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

    diff("enabled",           readOpt(optEnabled),    function(v) b.setEnabled(v) end)
    diff("adaptive",          readOpt(optAdaptive),   function(v) b.setAdaptive(v) end)
    diff("dynamicResolution", readOpt(optDynamicRes), function(v) b.setDynamicResolution(v) end)
    diff("meshBudgetEnabled", readOpt(optMeshBudget), function(v) b.setMeshBudgetEnabled(v) end)
    diff("chunkWalkThrottle", readOpt(optWalkThrottle), function(v) b.setChunkWalkThrottle(v) end)
    diff("drawDistanceAdaptive", readOpt(optDrawDist), function(v) b.setDrawDistanceAdaptive(v) end)
    diff("statsExport",       readOpt(optStatsExport), function(v) b.setStatsExport(v) end)
    diff("overlay",           readOpt(optOverlay),    function(v) b.setOverlayEnabled(v) end)
    diff("targetFps",         readOpt(optTargetFps),  function(v) b.setTargetFps(v) end)
    diff("renderScaleFixed",  readOpt(optScaleFixed), function(v) b.setRenderScaleFixed(v) end)
    diff("renderScaleMin",    readOpt(optScaleMin),   function(v) b.setRenderScaleMin(v) end)
    diff("meshBudgetMs",      readOpt(optMeshMs),     function(v) b.setMeshBudgetMs(v) end)
    diff("chunkWalkThrottleMs", readOpt(optWalkMs),   function(v) b.setChunkWalkThrottleMs(v) end)
    diff("drawDistanceMin",   readOpt(optDrawDistMin), function(v) b.setDrawDistanceMin(v) end)
end

-- apply once when a world loads, then keep in sync on a slow tick
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
