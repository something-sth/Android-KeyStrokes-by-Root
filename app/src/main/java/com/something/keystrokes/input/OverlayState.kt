package com.something.keystrokes.input


object OverlayState {


    private var pressedKeys =
        emptySet<Int>()


    private var listeners =
        mutableListOf<(Set<Int>) -> Unit>()



    fun update(
        keys: Set<Int>
    ) {

        pressedKeys = keys


        listeners.forEach {
            it(keys)
        }
    }



    fun getPressedKeys(): Set<Int> {

        return pressedKeys
    }



    fun addListener(
        listener: (Set<Int>) -> Unit
    ) {

        listeners.add(listener)

    }



    fun removeListener(
        listener: (Set<Int>) -> Unit
    ) {

        listeners.remove(listener)

    }


}