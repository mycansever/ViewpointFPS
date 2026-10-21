--[[---------------------------------------------------------------------------
    Viewpoint FPS Optimization v3.0.1 - client bootstrap
    Target: Project Zomboid Build 42.21 + Project Viewpoint (via ZombieBuddy)

    Keys (rebindable in Options > MODS):
      F9  - toggle the performance overlay
      F8  - toggle the in-game settings panel (governor, frame cap, memory)

    KAHLUA NOTE: pcall in this Lua VM does NOT catch "call nil" errors
    (they escape as RuntimeException and kill the whole event handler),
    so this file NEVER calls a possibly-nil value - everything is
    nil-checked before use. Keep that invariant when editing.

    NOTE: do not bind F10 - Windows opens the window menu on F10 and
    freezes the rendered frame (an OS behaviour, not this mod).
---------------------------------------------------------------------------]]--

VPFPS = VPFPS or {}
VPFPS.MOD_ID = "ViewpointFPS"
VPFPS.VERSION = "3.0.2"

local bridge, bridgeWarned = nil, false

--- Returns the Java bridge table, or nil when unavailable.
function VPFPS.getBridge()
    if bridge ~= nil then return bridge end
    if VPFPSBridge == nil then return nil end
    bridge = VPFPSBridge
    return bridge
end

--- Safe bridge call: VPFPS.call("setTargetFps", 60).
--- Nil-safe for Kahlua: the method is nil-checked BEFORE any call, and
--- the call itself runs inside pcall. Never throws.
function VPFPS.call(method, ...)
    local b = VPFPS.getBridge()
    if b == nil then
        if not bridgeWarned then
            bridgeWarned = true
            print("[VPFPS] Java bridge not reachable from Lua - the Java side still runs on its own;")
            print("[VPFPS] tune it by editing  <user>\\Zomboid\\vpfps.properties  (hot-reloads within ~5 s)")
        end
        return nil
    end
    local fn = b[method]
    if fn == nil then
        print("[VPFPS] bridge has no method: " .. tostring(method))
        return nil
    end
    local ok, res = pcall(fn, ...)
    if not ok then
        print("[VPFPS] bridge call failed: " .. tostring(method) .. " -> " .. tostring(res))
        return nil
    end
    return res
end

--- Is the Project Viewpoint mod active in this session?
--- Kahlua-safe: getActiveMods / :contains are nil-checked first.
local function isViewpointActive()
    if getActiveMods == nil then return false end
    local mods = getActiveMods()
    if mods == nil or mods.contains == nil then return false end
    local ok, res = pcall(mods.contains, mods, "Viewpoint")
    return ok and res == true
end

-- ---------------------------------------------------------------------------
-- keys: F9 overlay, F8 panel
-- ---------------------------------------------------------------------------

local function onKeyToggle(key)
    local opts = VPFPS.options
    if opts == nil then return end

    local function keyValue(opt)
        if opt == nil or opt.getValue == nil then return nil end
        local ok, v = pcall(opt.getValue, opt)
        if ok then return v end
        return nil
    end

    -- overlay toggle
    local overlayKey = keyValue(opts.overlayKey)
    if overlayKey ~= nil and key == overlayKey then
        local on = VPFPS.call("isOverlayEnabled")
        if on ~= nil then VPFPS.call("setOverlayEnabled", not on) end
        return
    end

    -- panel toggle
    local panelKey = keyValue(opts.panelKey)
    if panelKey ~= nil and key == panelKey then
        if VPFPS.togglePanel ~= nil then VPFPS.togglePanel() end
        return
    end
end

-- OnKeyStartPressed fires once per physical press (no auto-repeat)
local keyEvent = Events.OnKeyStartPressed or Events.OnKeyPressed
if keyEvent ~= nil and keyEvent.Add ~= nil then keyEvent.Add(onKeyToggle) end

-- ---------------------------------------------------------------------------
-- session start: report status once
-- ---------------------------------------------------------------------------

Events.OnGameStart.Add(function()
    local b = VPFPS.getBridge()
    if b ~= nil then
        print("[VPFPS] bridge online: " .. tostring(VPFPS.call("ping")))
        print("[VPFPS] capabilities: " .. tostring(VPFPS.call("getCapabilities")))
    else
        print("[VPFPS] running standalone (no Lua bridge); see Zomboid/Logs/vpfps.log")
    end
    if not isViewpointActive() then
        print("[VPFPS] NOTE: Project Viewpoint does not appear in the active mod list -")
        print("[VPFPS] this mod optimizes Viewpoint's renderer; enable it in Mods to benefit.")
    end
end)
