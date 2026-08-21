package com.something.keystrokes.view


import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.View



class KeyOverlayView(
    context: Context
) : View(context) {



    private val TAG =
        "KeyOverlayView"



    private val paint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        )



    private var pressedKeys =
        emptySet<Int>()



    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {


        val width =
            300


        val height =
            420


        setMeasuredDimension(
            width,
            height
        )


    }




    private val keyMap =
        mapOf(
            17 to "W",
            30 to "A",
            31 to "S",
            32 to "D",
            57 to "SPACE",
            272 to "LMB",
            273 to "RMB"
        )





    fun updateKeys(
        keys: Set<Int>
    ){

        Log.d(
            TAG,
            "updateKeys=$keys"
        )


        pressedKeys =
            keys.toSet()


        post {

            invalidate()

        }

    }






    override fun onDraw(
        canvas: Canvas
    ) {


        super.onDraw(canvas)



        Log.d(
            TAG,
            "onDraw"
        )



        val keySize =
            80f



        val gap =
            10f



        val center =
            width / 2f




        val normalColor =
            Color.argb(
                180,
                0,
                0,
                0
            )



        val pressedColor =
            Color.argb(
                180,
                255,
                255,
                255
            )



        paint.textAlign =
            Paint.Align.CENTER



        paint.textSize =
            35f



        paint.typeface =
            android.graphics.Typeface.DEFAULT




        /*
            W
        */


        drawKey(
            canvas,
            "W",
            center,
            10f,
            keySize,
            keySize,
            pressedKeys.contains(17),
            normalColor,
            pressedColor
        )




        /*
            ASD
        */


        val asdY =
            keySize + gap + 10f



        drawKey(
            canvas,
            "A",
            center-keySize-gap,
            asdY,
            keySize,
            keySize,
            pressedKeys.contains(30),
            normalColor,
            pressedColor
        )



        drawKey(
            canvas,
            "S",
            center,
            asdY,
            keySize,
            keySize,
            pressedKeys.contains(31),
            normalColor,
            pressedColor
        )



        drawKey(
            canvas,
            "D",
            center+keySize+gap,
            asdY,
            keySize,
            keySize,
            pressedKeys.contains(32),
            normalColor,
            pressedColor
        )




        /*
            LMB / RMB
        */


        val mouseY =
            asdY + keySize + gap

        val mouseWidth =
            keySize * 1.5f + gap * 0.5f



        drawKey(
            canvas,
            "LMB",
            center - mouseWidth / 2f - gap / 2f,
            mouseY,
            mouseWidth,
            keySize,
            pressedKeys.contains(272),
            normalColor,
            pressedColor
        )



        drawKey(
            canvas,
            "RMB",
            center + mouseWidth / 2f + gap / 2f,
            mouseY,
            mouseWidth,
            keySize,
            pressedKeys.contains(273),
            normalColor,
            pressedColor
        )




        /*
            SPACE
        */


        val longWidth =
            keySize*3 + gap*2



        val spaceY =
            mouseY + keySize + gap



        drawKey(
            canvas,
            "————",
            center,
            spaceY,
            longWidth,
            55f,
            pressedKeys.contains(57),
            normalColor,
            pressedColor
        )


    }






    private fun drawKey(
        canvas: Canvas,
        text: String,
        centerX: Float,
        topY: Float,
        w: Float,
        h: Float,
        pressed: Boolean,
        normal: Int,
        active: Int
    ){



        paint.color =
            if(pressed)
                active
            else
                normal




        canvas.drawRect(
            centerX-w/2,
            topY,
            centerX+w/2,
            topY+h,
            paint
        )




        paint.color =
            if(pressed)
                Color.BLACK
            else
                Color.WHITE




        canvas.drawText(
            text,
            centerX,
            topY+h/2+12,
            paint
        )


    }



}