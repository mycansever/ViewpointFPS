--[[---------------------------------------------------------------------------
    Viewpoint FPS Optimization v1.0.0 - client bootstrap
    Target: Project Zomboid Build 42.21 + Project Viewpoint (via ZombieBuddy)

    This file wires the Lua side to the Java side (global "VPFPSBridge",
    exposed by dev.vpfps.Main through PZ's ClassExposer). If the bridge is
    not reachable the Java side still runs standalone - tuning then happens
    by editing  <user>\Zomboid\vpfps.properties  (hot-reloads in ~5 s).
---------------------------------------------------------------------------]]--

VPFPS = VPFPS or {}
VPFPS.MOD_ID = "ViewpointFPS"
VPFPS.VERSION = "1.0.0"

local bridge, bridgeWarned = nil, false

--- Returns the Java bridge table, or nil when unavailable.
function VPFPS.getBridge()
    if bridge ~= nil then return bridge end
    local ok, b = pcall(function() return VPFFSBridge end)
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
-- keybind: toggle the overlay (default F9, rebindable in Options > MODS)
-- ---------------------------------------------------------------------------

local function onKeyToggle(key)
    local opts = VPFPS.options
    if opts == nil or opts.overlayKey == nil then return end

    local bound
    local ok, v = pcall(function() return opts.overlayKey:getValue() end)
    bound = ok and v or nil
    if bound == nil or key ~= bound then return end

    local b = VPFPS.getBridge()
    if b then
        local on = b.isOverlayEnabled()
        if on ~= nil then
            b.setOverlayEnabled(not on)
            return
        end
    end
    print("[VPFPS] overlay toggle needs the Java bridge (or set overlay=... in vpfps.properties)")
end

-- OnKeyStartPressed fires once per physical press (no auto-repeat);
-- fall back to OnKeyPressed on builds where it does not exist.
local keyEvent = Events.OnKeyStartPressed or Events.OnKeyPressed
if keyEvent then keyEvent.Add(onKeyToggle) end

-- ---------------------------------------------------------------------------
-- session start: report status once
-- ---------------------------------------------------------------------------

Events.OnGameStart.Add(function()
    local b = VPFPS.getBridge()
    if b then
        print("[VPFPS] bridge online: " .. tostring(b.ping()))
        print("[VPFPS] Viewpoint detected: " .. tostring(b.isViewpointPresent()))
        print("[VPFPS] capabilities: " .. tostring(b.getCapabilities()))
    else
        print("[VPFPS] running standalone (no Lua bridge); see Zomboid/Logs/vpfps.log")
    end
    if not isViewpointActive() then
        print("[VPFPS] WARNING: Project Viewpoint does not appear to be enabled -")
        print("[VPFPS] this mod optimizes Viewpoint's renderer; enable it in Mods to benefit.")
    end
end)
