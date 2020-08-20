package com.dragonfly.bt.client

import org.junit.Test

class ReadWriteTest {

    @Test
    fun byte2String (){
        val mHi = arrayOf("hi", "Hello", "你好", "hanihaseiyo")
        val byteArray = mHi[(0..3).random()].toByteArray()
        println(byteArray)

        println(String(byteArray))
    }
}