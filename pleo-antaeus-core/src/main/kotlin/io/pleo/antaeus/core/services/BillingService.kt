package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.InvoiceStatus
import java.util.*
import kotlin.concurrent.schedule


class BillingService (
        private val paymentProvider: PaymentProvider,
        private val invoiceService: InvoiceService,
        private var periodUnit : Int,
        private var periodAmount : Int,
        private var firstOfMonth: Boolean
) {

    private val timer: Timer = Timer("schedule", true)

    fun schedulePeriodicBilling() {
        val scheduledTime = GregorianCalendar.getInstance()
        if (firstOfMonth){
            scheduledTime.set(Calendar.DAY_OF_MONTH, 1)
            scheduledTime.set(Calendar.HOUR_OF_DAY, 8)
            scheduledTime.set(Calendar.MINUTE, 0)
        }
        scheduledTime.add(periodUnit, periodAmount)
        timer.schedule(scheduledTime.time) {
            makePaymentAndScheduleNewOne()
        }
   }

    private fun makePaymentAndScheduleNewOne() {
        makePaymentForPending()
        schedulePeriodicBilling()
    }

    private fun makePaymentForPending() {
        //TODO debug log here
        println("make payment")
        val invoices = invoiceService.fetchAll()
        for (invoice in invoices){
            if (invoice.status == InvoiceStatus.PENDING){
                val result = paymentProvider.charge(invoice)
                if (result) {
                    val paidInvoice = invoice.copy(status = InvoiceStatus.PAID)
                    invoiceService.update(paidInvoice)
                } else {
                    val failedInvoice = invoice.copy(status = InvoiceStatus.FAILED)
                    invoiceService.update(failedInvoice)
                }
            }

        }
    }
}