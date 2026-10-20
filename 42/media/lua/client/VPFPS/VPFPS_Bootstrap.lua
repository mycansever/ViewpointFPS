--[[---------------------------------------------------------------------------
    Viewpoint FPS Optimization v3.0.0 - client bootstrap
    Target: Project Zomboid Build 42.21 + Project Viewpoint (via ZombieBuddy)

    Keys (rebindable in Options > MODS):
      F9  - toggle the performance overlay
      F8  - toggle the in-game settings panel (governor, frame cap, memory)

    NOTE: do not bind F10 - Windows opens the window menu on F10 and freezes
    the rendered frame (an OS behaviour, not caused by this mod).
---------------------------------------------------------------------------]]--

VPFPS = VPFPS or {}
VPFPS.MOD_ID = "ViewpointFPS"
VPFPS.VERSION = "3.0.0"

local bridge, bridgeWarned = nil, false

--- Returns the Java bridge table, or nil when unavailable.
function VPFPS.getBridge()
    if bridge ~= nil then return bridge end
    local ok, b = pcall(function() return VPFPSBridge end)
    if ok and b ~= nil then
        bridge = b
        return bridge
    end
    return nil
end

--- Safe bridge call: VPFPS.call("setTargetFps", 60). Never throws.
function VPFPS.call(method, ...)
    local b = VPFPS.getBridge()
    if not b then
        if not bridgeWarned then
            bridgeWarned = true
            print("[VPFPS] Java bridge not reachable from Lua - the Java side still runs on its own;")
            print("[VPFPS] tune it by editing  <user>\\Zomboid\\vpfps.properties  (hot-reloads within ~5 s)")
        end
        return nil
    end
    local fn = b[method]
    if type(fn) ~= "function" then return nil end
    local ok, res = pcall(fn, ...)
    if not ok then
        print("[VPFPS] bridge call failed: " .. tostring(method) .. " -> " .. tostring(res))
        return nil
    end
    return res
end

--- Is the Project Viewpoint mod active in this session?
local function isViewpointActive()
    local ok, res = pcall(function() return getActiveMods():contains("Viewpoint") end)
    return ok and res == true
end

-- ---------------------------------------------------------------------------
-- keys: F9 overlay, F8 panel
-- ---------------------------------------------------------------------------

local function onKeyToggle(key)
    local opts = VPFPS.options
    if opts == nil then return end

    local function keyValue(opt)
        if opt == nil then return nil end
        local ok, v = pcall(function() return opt:getValue() end)
        if ok then return v end
        return nil
    end

    -- overlay toggle
    local overlayKey = keyValue(opts.overlayKey)
    if overlayKey ~= nil and key == overlayKey then
        local b = VPFPS.getBridge()
        if b then
            local on = b.isOverlayEnabled()
            if on ~= nil then b.setOverlayEnabled(not on) end
        end
        return
    end

    -- panel toggle
    local panelKey = keyValue(opts.panelKey)
    if panelKey ~= nil and key == panelKey then
        if VPFPS.togglePanel then VPFPS.togglePanel() end
        return
    end
end

-- OnKeyStartPressed fires once per physical press (no auto-repeat)
local keyEvent = Events.OnKeyStartPressed or Events.OnKeyPressed
if keyEvent then keyEvent.Add(onKeyToggle) end

-- ---------------------------------------------------------------------------
-- session start: report status once, bind memory pools early
-- ---------------------------------------------------------------------------

Events.OnGameStart.Add(function()
    local b = VPFPS.getBridge()
    if b then
        print("[VPFPS] bridge online: " .. tostring(b.ping()))
        print("[VPFPS] Viewpoint detected: " .. tostring(b.getCapabilities()))
    else
        print("[VPFPS] running standalone (no Lua bridge); see Zomboid/Logs/vpfps.log")
    end
    if not isViewpointActive() then
        print("[VPFPS] WARNING: Project Viewpoint does not appear to be enabled -")
        print("[VPFPS] this mod optimizes Viewpoint's renderer; enable it in Mods to benefit.")
    end
end)
