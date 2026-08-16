const CAMPUS_CHROME_EVENT = 'campus:chrome-visibility'
const CAMPUS_CHROME_CLASS = 'campus-scroll-chrome-hidden'
const TOP_EDGE = 18
// Require a deliberate downward travel, but let an upward gesture recover sooner.
const HIDE_THRESHOLD = 64
const SHOW_THRESHOLD = 30

function clampProgress(value) {
	return Math.max(0, Math.min(1, Number(value) || 0))
}

function setRootChromeClass(hidden) {
	if (typeof document === 'undefined') return
	const targets = [document.documentElement, document.body]
	targets.forEach((target) => {
		if (target) target.classList.toggle(CAMPUS_CHROME_CLASS, hidden)
	})
}

function setRootChromeProgress(progress) {
	if (typeof document === 'undefined') return
	const value = clampProgress(progress)
	const targets = [document.documentElement, document.body]
	targets.forEach((target) => {
		if (!target || !target.style) return
		target.style.setProperty('--campus-chrome-progress', String(value))
		target.style.setProperty('--campus-chrome-opacity', String(1 - value))
		target.style.setProperty('--campus-chrome-header-shift', `${(-110 * value).toFixed(3)}%`)
		target.style.setProperty('--campus-chrome-dock-shift', `${(96 * value).toFixed(3)}px`)
		target.style.setProperty('--campus-chrome-trigger-shift', `${(110 * value).toFixed(3)}px`)
		target.style.setProperty('--campus-chrome-trigger-scale', String(1 - (0.42 * value)))
	})
}

function publishChromeState(progress) {
	const value = clampProgress(progress)
	const hidden = value >= 0.999
	setRootChromeProgress(value)
	setRootChromeClass(hidden)
	try {
		if (typeof uni !== 'undefined' && uni.$emit) {
			uni.$emit(CAMPUS_CHROME_EVENT, { hidden, progress: value })
		}
	} catch (error) {
		// Some runtimes do not expose the global event bus during startup.
	}
	return value
}

export function setCampusChromeHidden(hidden) {
	return publishChromeState(Boolean(hidden) ? 1 : 0)
}

export function resetCampusChromeScroll(vm) {
	if (vm) {
		clearTimeout(vm._campusChromeWheelTimer)
		vm._campusChromeInitialized = false
		vm._campusChromeLastTop = 0
		vm._campusChromeDirection = ''
		vm._campusChromeDistance = 0
		vm._campusChromeHidden = false
		vm._campusChromePendingWheel = 0
		vm._campusChromeGestureTop = 0
		vm._campusChromeNativeScrollAt = 0
		vm._campusChromeTouchY = null
		vm._campusChromeProgress = 0
	}
	setCampusChromeHidden(false)
}

export function bindCampusChromeScroll(vm) {
	if (!vm || typeof window === 'undefined' || vm._campusChromeWindowHandler) return
	const readTop = (event) => {
		const documentTop = typeof document !== 'undefined' && document.documentElement
			? document.documentElement.scrollTop
			: 0
		const bodyTop = typeof document !== 'undefined' && document.body ? document.body.scrollTop : 0
		const targetTop = event && event.target && typeof event.target.scrollTop === 'number'
			? event.target.scrollTop
			: 0
		return Math.max(window.pageYOffset || 0, documentTop || 0, bodyTop || 0, targetTop || 0)
	}
	const handler = (event) => {
		const top = readTop(event)
		vm._campusChromeGestureTop = top
		vm._campusChromeNativeScrollAt = Date.now()
		handleCampusChromeScroll(vm, top)
	}
	vm._campusChromeWindowHandler = handler
	window.addEventListener('scroll', handler, { passive: true, capture: true })
	if (typeof document !== 'undefined' && document.addEventListener) {
		document.addEventListener('scroll', handler, { passive: true, capture: true })
	}

	// Some H5 shells move the page through a native gesture layer without
	// dispatching a regular scroll event. Use wheel/touch intent as a fallback.
	const gestureTarget = typeof document !== 'undefined' && document.addEventListener ? document : window
	const applyGestureDelta = (delta) => {
		if (!delta) return
		const base = Number.isFinite(Number(vm._campusChromeGestureTop))
			? Number(vm._campusChromeGestureTop)
			: readTop()
		const next = Math.max(0, base + delta)
		vm._campusChromeGestureTop = next
		handleCampusChromeScroll(vm, next)
	}
	const flushWheel = () => {
		const delta = Number(vm._campusChromePendingWheel) || 0
		vm._campusChromePendingWheel = 0
		if (!delta) return
		if (Date.now() - (vm._campusChromeNativeScrollAt || 0) > 80) applyGestureDelta(delta)
	}
	const wheelHandler = (event) => {
		const delta = Number(event && event.deltaY) || 0
		if (!delta) return
		vm._campusChromePendingWheel = (Number(vm._campusChromePendingWheel) || 0) + delta
		clearTimeout(vm._campusChromeWheelTimer)
		vm._campusChromeWheelTimer = setTimeout(flushWheel, 48)
	}
	const touchStartHandler = (event) => {
		const touch = event && event.touches && event.touches[0]
		if (!touch) return
		vm._campusChromeTouchY = touch.clientY
		vm._campusChromeGestureTop = readTop(event)
	}
	const touchMoveHandler = (event) => {
		const touch = event && event.touches && event.touches[0]
		if (!touch || !Number.isFinite(Number(vm._campusChromeTouchY))) return
		const currentY = touch.clientY
		const delta = vm._campusChromeTouchY - currentY
		vm._campusChromeTouchY = currentY
		if (Math.abs(delta) < 1) return
		if (Date.now() - (vm._campusChromeNativeScrollAt || 0) > 80) applyGestureDelta(delta)
	}
	const touchEndHandler = () => {
		vm._campusChromeTouchY = null
	}
	vm._campusChromeGestureTarget = gestureTarget
	vm._campusChromeWheelHandler = wheelHandler
	vm._campusChromeTouchStartHandler = touchStartHandler
	vm._campusChromeTouchMoveHandler = touchMoveHandler
	vm._campusChromeTouchEndHandler = touchEndHandler
	gestureTarget.addEventListener('wheel', wheelHandler, { passive: true, capture: true })
	gestureTarget.addEventListener('touchstart', touchStartHandler, { passive: true, capture: true })
	gestureTarget.addEventListener('touchmove', touchMoveHandler, { passive: true, capture: true })
	gestureTarget.addEventListener('touchend', touchEndHandler, { passive: true, capture: true })
	handler()
}

export function unbindCampusChromeScroll(vm) {
	if (!vm || typeof window === 'undefined' || !vm._campusChromeWindowHandler) return
	window.removeEventListener('scroll', vm._campusChromeWindowHandler, true)
	if (typeof document !== 'undefined' && document.removeEventListener) {
		document.removeEventListener('scroll', vm._campusChromeWindowHandler, true)
	}
	clearTimeout(vm._campusChromeWheelTimer)
	const gestureTarget = vm._campusChromeGestureTarget
	if (gestureTarget && vm._campusChromeWheelHandler) {
		gestureTarget.removeEventListener('wheel', vm._campusChromeWheelHandler, true)
		gestureTarget.removeEventListener('touchstart', vm._campusChromeTouchStartHandler, true)
		gestureTarget.removeEventListener('touchmove', vm._campusChromeTouchMoveHandler, true)
		gestureTarget.removeEventListener('touchend', vm._campusChromeTouchEndHandler, true)
	}
	vm._campusChromeGestureTarget = null
	vm._campusChromeWheelHandler = null
	vm._campusChromeTouchStartHandler = null
	vm._campusChromeTouchMoveHandler = null
	vm._campusChromeTouchEndHandler = null
	vm._campusChromeWindowHandler = null
}

export function handleCampusChromeScroll(vm, rawTop) {
	if (!vm) return
	const top = Math.max(0, Number(rawTop) || 0)
	if (!vm._campusChromeInitialized) {
		vm._campusChromeInitialized = true
		vm._campusChromeLastTop = top
		vm._campusChromeDirection = ''
		vm._campusChromeDistance = 0
		vm._campusChromeProgress = 0
		return
	}

	if (top <= TOP_EDGE) {
		vm._campusChromeLastTop = top
		vm._campusChromeDirection = ''
		vm._campusChromeDistance = 0
		vm._campusChromeProgress = 0
		vm._campusChromeHidden = false
		publishChromeState(0)
		return
	}

	const delta = top - vm._campusChromeLastTop
	vm._campusChromeLastTop = top
	if (!delta || Math.abs(delta) < 1) return

	const direction = delta > 0 ? 'down' : 'up'
	if (direction !== vm._campusChromeDirection) {
		vm._campusChromeDirection = direction
		vm._campusChromeDistance = 0
		vm._campusChromeStartProgress = clampProgress(vm._campusChromeProgress)
	}
	vm._campusChromeDistance += Math.abs(delta)

	const threshold = direction === 'down' ? HIDE_THRESHOLD : SHOW_THRESHOLD
	const startProgress = clampProgress(vm._campusChromeStartProgress)
	const nextProgress = direction === 'down'
		? Math.min(1, startProgress + (vm._campusChromeDistance / threshold))
		: Math.max(0, startProgress - (vm._campusChromeDistance / threshold))
	if (Math.abs(nextProgress - clampProgress(vm._campusChromeProgress)) < 0.01) return
	vm._campusChromeProgress = nextProgress
	vm._campusChromeHidden = nextProgress >= 0.999
	publishChromeState(nextProgress)
}

export { CAMPUS_CHROME_EVENT }
