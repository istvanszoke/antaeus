package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import java.util.*
import kotlin.concurrent.schedule


class BillingService(
        private val paymentProvider: PaymentProvider,
        private val invoiceService: InvoiceService
) {

    private val timer: Timer = Timer("schedule", true)

    fun scheduleInfinitePeriodicBilling(periodUnit: Int, periodAmount: Int, firstOfMonth: Boolean) {
        val scheduledTime = GregorianCalendar.getInstance()
        if (firstOfMonth) {
            scheduledTime.set(Calendar.DAY_OF_MONTH, 1)
            scheduledTime.set(Calendar.HOUR_OF_DAY, 8)
            scheduledTime.set(Calendar.MINUTE, 0)
        }
        scheduledTime.add(periodUnit, periodAmount)
        timer.schedule(scheduledTime.time) {
            makePaymentForPending()
            scheduleInfinitePeriodicBilling(periodUnit,periodAmount,firstOfMonth)
        }
    }

    fun scheduleSimplePeriodicBilling() {

    }

    private fun makePaymentForPending() {
        //TODO debug log here
        println("make payment")
        val invoices = invoiceService.fetchAll()
        for (invoice in invoices) {
            if (invoice.status == InvoiceStatus.PENDING) {
                val result = paymentProvider.charge(invoice)
                handleChargeResult(invoice, result)
            }

        }
    }

    private fun makePaymentForFailed() {
        val invoices = invoiceService.fetchAll()
        for (invoice in invoices) {
            if (invoice.status == InvoiceStatus.FAILED) {
                val result = paymentProvider.charge(invoice)
                handleChargeResult(invoice, result)
            }

        }
    }

    private fun handleChargeResult(invoice: Invoice, result: Boolean) {
        if (result) {
            val paidInvoice = invoice.copy(status = InvoiceStatus.PAID)
            invoiceService.update(paidInvoice)
        } else {
            val failedInvoice = invoice.copy(status = InvoiceStatus.FAILED)
            invoiceService.update(failedInvoice)
        }
    }
}