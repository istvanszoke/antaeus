package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import java.util.*
import kotlin.concurrent.schedule


class BillingService(
    private val paymentProvider: PaymentProvider
) {
    private val timer: Timer = Timer("schedule", true)

    fun schedulePayment(): Unit {
        println("I am scheduled")
        val scheduledTime = GregorianCalendar.getInstance()
        //scheduledTime.add(Calendar.MONTH, 1)
        //scheduledTime.set(Calendar.DAY_OF_MONTH, 1)
        //scheduledTime.set(Calendar.HOUR_OF_DAY, 8)
        //scheduledTime.set(Calendar.MINUTE, 0)
        scheduledTime.add(Calendar.SECOND, 10)
        timer.schedule(scheduledTime.time) {
            schedulePayment()
        }
   }
}