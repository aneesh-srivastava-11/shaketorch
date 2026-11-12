package com.shaketorch.sensor

/**
 * Functional interface for receiving shake gesture events.
 */
fun interface ShakeListener {
    /**
     * Invoked when an intentional, valid shake gesture is detected.
     */
    fun onShake()
}

