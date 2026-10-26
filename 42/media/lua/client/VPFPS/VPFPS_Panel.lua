--[[---------------------------------------------------------------------------
    Viewpoint FPS Optimization v3.0.0 - in-game settings panel (F8)

    Everything is adjustable IN-GAME (no file editing):
      - live stats header (fps, 1% low, GPU busy, VRAM, factors)
      - governor: mode, target fps, CPU/GPU floors, ladders on/off
      - frame cap: Viewpoint's own FrameCaps (game + menu, uncap button)
      - memory: RAM/VRAM pool scale, restore stock
      - overlay: on/off, detail, CSV export

    Built only on ISPanel + ISButton + drawText - the most stable UI
    surface across builds. Steppers (+/-) and toggle buttons instead of
    sliders/tickboxes on purpose: zero API risk, same usability.
---------------------------------------------------------------------------]]--

require "ISUI/ISPanel"
require "ISUI/ISButton"

local FONT_S = UIFont.Small
local FONT_M = UIFont.Medium

local COL_BG      = { r = 0.06, g = 0.08, b = 0.10, a = 0.94 }
local COL_BORDER  = { r = 0.30, g = 0.75, b = 0.40, a = 0.8 }
local COL_TITLE   = { r = 0.75, g = 0.95, b = 0.80, a = 1.0 }
local COL_TEXT    = { r = 0.85, g = 0.88, b = 0.90, a = 1.0 }
local COL_DIM     = { r = 0.55, g = 0.60, b = 0.62, a = 1.0 }
local COL_GOOD    = { r = 0.40, g = 0.95, b = 0.50, a = 1.0 }
local COL_WARN    = { r = 0.95, g = 0.80, b = 0.30, a = 1.0 }
local COL_BAD     = { r = 0.95, g = 0.40, b = 0.35, a = 1.0 }

local function call(method, ...)
    return VPFPS.call(method, ...)
end

local function fmt(v, decimals)
    if v == nil then return "n/a" end
    if v ~= v then return "n/a" end          -- NaN (NaN ~= NaN in Lua)
    if v < 0 then return "n/a" end           -- -1 = feature unavailable
    return string.format("%." .. (decimals or 0) .. "f", v)
end

VPFPS_Panel = ISPanel:derive("VPFPS_Panel")

-- ---------------------------------------------------------------------------
-- construction helpers
-- ---------------------------------------------------------------------------

function VPFPS_Panel:initialise()
    ISPanel.initialise(self)
    self.rowY = 96
    self.statCache = {}
    self.statTick = 99
    self:buildControls()
end

function VPFPS_Panel:drawStatic(text, y, font, color)
    self.statics = self.statics or {}
    table.insert(self.statics, { text = text, y = y, font = font, color = color })
end

function VPFPS_Panel:addHeader(text)
    self:drawStatic(text, self.rowY, FONT_M, COL_TITLE)
    self.rowY = self.rowY + 26
end

function VPFPS_Panel:addToggle(labelText, getter, setter)
    local y = self.rowY
    self:drawStatic(labelText, y + 6, FONT_S, COL_TEXT)
    local btn = ISButton:new(self.width - 150, y, 120, 24, "...", self, VPFPS_Panel.onToggle)
    btn.userGetter = getter
    btn.userSetter = setter
    self:addChild(btn)
    table.insert(self.toggles, btn)
    self.rowY = self.rowY + 32
end

function VPFPS_Panel:addStepper(labelText, getter, setter, minV, maxV, stepV, decimals)
    local y = self.rowY
    self:drawStatic(labelText, y + 6, FONT_S, COL_TEXT)
    local minus = ISButton:new(self.width - 190, y, 30, 24, "-", self, VPFPS_Panel.onStep)
    local plus  = ISButton:new(self.width - 52,  y, 30, 24, "+", self, VPFPS_Panel.onStep)
    for _, b in ipairs({ minus, plus }) do
        b.userGetter = getter
        b.userSetter = setter
        b.userMin = minV
        b.userMax = maxV
        b.userDecimals = decimals
    end
    minus.userStep = -stepV
    plus.userStep = stepV
    self:addChild(minus)
    self:addChild(plus)
    table.insert(self.steppers, { plus = plus, getter = getter, decimals = decimals, y = y })
    self.rowY = self.rowY + 32
end

function VPFPS_Panel:addButton(labelText, title, fn)
    local y = self.rowY
    if labelText then
        self:drawStatic(labelText, y + 6, FONT_S, COL_TEXT)
    end
    local btn = ISButton:new(self.width - 240, y, 220, 24, title, self, fn)
    self:addChild(btn)
    self.rowY = self.rowY + 32
    return btn
end

function VPFPS_Panel:spacer(h)
    self.rowY = self.rowY + (h or 8)
end

-- ---------------------------------------------------------------------------
-- control handlers
-- ---------------------------------------------------------------------------

function VPFPS_Panel:onToggle(button)
    -- the governor row cycles adaptive -> fixed -> off
    if button.userGetter == "getGovernor" then
        local current = call("getGovernor")
        local nextMode = "adaptive"
        if current == "adaptive" then nextMode = "fixed"
        elseif current == "fixed" then nextMode = "off" end
        call("setGovernor", nextMode)
    else
        local current = call(button.userGetter)
        if current == nil then return end
        call(button.userSetter, not current)
    end
    self:refreshControls()
end

function VPFPS_Panel:onStep(button)
    local current = call(button.userGetter)
    if current == nil or current < 0 then return end
    local step = math.abs(button.userStep)
    local v = current + button.userStep
    v = math.max(button.userMin, math.min(button.userMax, v))
    if step >= 1 then
        v = math.floor(v + 0.5)
    else
        v = math.floor(v * 100 + 0.5) / 100
    end
    -- clamp again after rounding (e.g. 1.00 + 0.05 rounded to 1.00)
    v = math.max(button.userMin, math.min(button.userMax, v))
    call(button.userSetter, v)
    self:refreshControls()
end

function VPFPS_Panel:onClose()
    VPFPS.setPanelVisible(false)
end

function VPFPS_Panel:onUncap()
    call("uncapGame")
    self:refreshControls()
end

function VPFPS_Panel:onRestoreStock()
    call("restoreStock")
    call("setMemoryScale", 1.0)
    self:refreshControls()
end

-- ---------------------------------------------------------------------------
-- refresh + render
-- ---------------------------------------------------------------------------

function VPFPS_Panel:refreshControls()
    for _, btn in ipairs(self.toggles) do
        if btn.userGetter == "getGovernor" then
            local v = call("getGovernor")
            btn:setTitle(v ~= nil and string.upper(v) or "N/A")
        else
            local v = call(btn.userGetter)
            btn:setTitle(v == nil and "N/A" or (v and "ON" or "OFF"))
        end
    end
    for _, s in ipairs(self.steppers) do
        local v = call(s.getter)
        s.value = fmt(v, s.decimals)
    end
end

function VPFPS_Panel:render()
    ISPanel.render(self)
    self:drawRectBorder(0, 0, self.width, self.height, COL_BORDER.a,
            COL_BORDER.r, COL_BORDER.g, COL_BORDER.b)

    -- live stats, throttled to ~4 Hz
    self.statTick = self.statTick + 1
    if self.statTick >= 15 then
        self.statTick = 0
        local c = self.statCache
        c.fps      = call("getFps")
        c.low      = call("getFpsOnePercentLow")
        c.frame    = call("getFrameTimeMs")
        c.p99      = call("getFrameTimeP99Ms")
        c.gpu      = call("getGpuBusyPercent")
        c.cpuF     = call("getCpuFactor")
        c.gpuF     = call("getGpuFactor")
        c.vrFree   = call("getVramFreeMiB")
        c.vrTotal  = call("getVramTotalMiB")
        c.mem      = call("getMemoryScale")
        c.target   = call("getTargetFps")
    end

    local c = self.statCache
    local y = 34
    local target = c.target or 60

    -- headline: presented fps when available (matches external GPU overlays),
    -- game-step fps as fallback
    local presented = call("getPresentedFps")
    local fps = presented ~= nil and presented > 0 and presented or (c.fps or 0)
    local col = COL_BAD
    if fps >= target * 0.95 then col = COL_GOOD
    elseif fps >= target * 0.70 then col = COL_WARN end
    local label = "FPS"
    if presented ~= nil and presented > 0 then label = "FPS (presented)" end
    self:drawTextRight(string.format("%s %s   (1%% low %s)", label, fmt(fps, 1), fmt(c.low, 1)),
            self.width - 48, y, col.r, col.g, col.b, 1.0, FONT_M)
    y = y + 22

    self:drawTextRight(string.format("frame %s ms   p99 %s ms   GPU %s",
            fmt(c.frame, 1), fmt(c.p99, 1),
            (c.gpu ~= nil and c.gpu >= 0) and (fmt(c.gpu, 0) .. "%") or "n/a"),
            self.width - 48, y, COL_TEXT.r, COL_TEXT.g, COL_TEXT.b, 1.0, FONT_S)
    y = y + 17

    local vram = "VRAM n/a"
    if c.vrTotal ~= nil and c.vrTotal >= 0 and c.vrFree ~= nil and c.vrFree >= 0 then
        vram = string.format("VRAM %s / %s MiB", fmt(c.vrTotal - c.vrFree, 0), fmt(c.vrTotal, 0))
    end
    self:drawTextRight(string.format("cpu x%s   gpu x%s   mem x%s   %s",
            fmt(c.cpuF, 2), fmt(c.gpuF, 2), fmt(c.mem, 2), vram),
            self.width - 48, y, COL_TEXT.r, COL_TEXT.g, COL_TEXT.b, 1.0, FONT_S)

    -- section headers + row labels
    if self.statics then
        for _, s in ipairs(self.statics) do
            self:drawText(s.text, 16, s.y, s.color.r, s.color.g, s.color.b, s.color.a, s.font)
        end
    end

    -- stepper values, centred between the - and + buttons
    for _, s in ipairs(self.steppers) do
        if s.value then
            self:drawTextCentre(s.value, self.width - 110, s.y + 6,
                    COL_TEXT.r, COL_TEXT.g, COL_TEXT.b, 1.0, FONT_S)
        end
    end

    -- footer: governor status + capabilities
    local status = call("getStatus")
    if status then
        self:drawText(status, 16, self.height - 40, COL_DIM.r, COL_DIM.g, COL_DIM.b, 1.0, FONT_S)
    end
    local knobs = call("getCapabilities")
    if knobs then
        self:drawText(knobs, 16, self.height - 24, COL_DIM.r, COL_DIM.g, COL_DIM.b, 1.0, FONT_S)
    end
end

function VPFPS_Panel:prerender()
    ISPanel.prerender(self)
    self:drawRect(0, 0, self.width, self.height, COL_BG.a, COL_BG.r, COL_BG.g, COL_BG.b)
    self:drawText("VIEWPOINT FPS OPTIMIZATION  -  " .. (VPFPS.VERSION or "?"),
            16, 10, COL_TITLE.r, COL_TITLE.g, COL_TITLE.b, 1.0, FONT_M)
end

-- ---------------------------------------------------------------------------
-- control surface
-- ---------------------------------------------------------------------------

function VPFPS_Panel:buildControls()
    self.toggles = {}
    self.steppers = {}
    self.statics = {}

    self:addHeader("GOVERNOR")
    self:addToggle("Governor mode  (adaptive / fixed / off)", "getGovernor", "setGovernor")
    self:addStepper("Target FPS", "getTargetFps", "setTargetFps", 30, 240, 5, 0)
    self:addStepper("CPU floor  (build/walk/job knobs)", "getCpuFactorMin", "setCpuFactorMin", 0.30, 1.0, 0.05, 2)
    self:addStepper("GPU floor  (distance/detail knobs)", "getGpuFactorMin", "setGpuFactorMin", 0.30, 1.0, 0.05, 2)
    self:addToggle("CPU ladder  (chunk builds, walks, far jobs)", "isCpuAdaptive", "setCpuAdaptive")
    self:addToggle("GPU ladder  (draw distance, far detail, corpses)", "isGpuAdaptive", "setGpuAdaptive")

    self:spacer(10)
    self:addHeader("FRAME CAP  (Viewpoint FrameCaps - no file editing)")
    self:addStepper("In-game FPS cap", "getGameCap", "setGameCap", 30, 300, 5, 0)
    self:addStepper("Main menu FPS cap", "getMenuCap", "setMenuCap", 30, 240, 5, 0)
    self:addButton("Remove the in-game FPS limit entirely", "UNCAP", VPFPS_Panel.onUncap)

    self:spacer(10)
    self:addHeader("MEMORY  (RAM / VRAM pools)")
    self:addStepper("Pool budget scale  (1.00 = stock)", "getMemoryScale", "setMemoryScale", 0.50, 1.50, 0.05, 2)
    self:addButton("Restore every Viewpoint setting we touched", "RESTORE STOCK", VPFPS_Panel.onRestoreStock)

    self:spacer(10)
    self:addHeader("OVERLAY & STATS")
    self:addToggle("Performance overlay (F9)", "isOverlayEnabled", "setOverlayEnabled")
    self:addToggle("Overlay detail lines", "isOverlayDetail", "setOverlayDetail")
    self:addToggle("CSV benchmark export (vpfps-stats.csv)", "isStatsExport", "setStatsExport")

    -- close button, top right
    local closeBtn = ISButton:new(self.width - 36, 8, 28, 26, "X", self, VPFPS_Panel.onClose)
    self:addChild(closeBtn)

    self:refreshControls()
end

-- ---------------------------------------------------------------------------
-- singleton management
-- ---------------------------------------------------------------------------

local panelInstance = nil

function VPFPS.setPanelVisible(visible)
    if panelInstance == nil and not visible then return end
    if panelInstance == nil then
        local w, h = 660, 706
        local ok, err = pcall(function()
            local x = (getCore():getScreenWidth() - w) / 2
            local y = (getCore():getScreenHeight() - h) / 2
            panelInstance = VPFPS_Panel:new(x, y, w, h)
            panelInstance:initialise()
            panelInstance:addToUIManager()
            panelInstance:setVisible(false)
            panelInstance:bringToTop()
        end)
        if not ok then
            print("[VPFPS] panel failed to build: " .. tostring(err))
            panelInstance = nil
            return
        end
    end
    panelInstance:setVisible(visible)
    if visible then
        panelInstance:refreshControls()
        panelInstance:bringToTop()
    end
end

function VPFPS.togglePanel()
    if panelInstance ~= nil then
        VPFPS.setPanelVisible(not panelInstance:isVisible())
    else
        VPFPS.setPanelVisible(true)
    end
end
