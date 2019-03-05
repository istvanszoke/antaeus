package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import java.util.*
import kotlin.concurrent.schedule


class BillingService(
        private val paymentProvider: PaymentProvider,
        private val invoiceService: InvoiceService,
        private val periodUnit: Int,
        private val periodAmount: Int,
        private val failedPeriodUnit: Int,
        private val failedPeriodAmount: Int,
        private val firstOfMonth: Boolean
) {
    val PENDING_STATE = 0
    val FAILED1_STATE = 1
    val FAILED2_STATE = 2
    val FAILED3_STATE = 3

    private val timer: Timer = Timer("billingScheduler", true)

    private var currentState = PENDING_STATE

    fun scheduleNext() {
        val time = GregorianCalendar.getInstance()
        when (currentState) {
            PENDING_STATE -> {
                val scheduledTime = getNextPendingDate(time.time)
                timer.schedule(scheduledTime) {
                    makePayment(InvoiceStatus.PENDING)
                    scheduleNext()
                }
                currentState = FAILED1_STATE
            }
            FAILED1_STATE -> {
                val scheduledTime = getNextFailedDate(time.time)
                timer.schedule(scheduledTime) {
                    makePayment(InvoiceStatus.FAILED1)
                    scheduleNext()
                }
                currentState = FAILED2_STATE
            }
            FAILED2_STATE -> {
                val scheduledTime = getNextFailedDate(time.time)
                timer.schedule(scheduledTime) {
                    makePayment(InvoiceStatus.FAILED2)
                    scheduleNext()
                }
                currentState = FAILED3_STATE
            }
            FAILED3_STATE -> {
                val scheduledTime = getNextFailedDate(time.time)
                timer.schedule(scheduledTime) {
                    makePayment(InvoiceStatus.FAILED3)
                    scheduleNext()
                }
                currentState = PENDING_STATE
            }
        }

    }

    private fun getNextPendingDate(date: Date) : Date {
        val scheduledTime = GregorianCalendar()
        scheduledTime.time = date
        if (firstOfMonth) {
            scheduledTime.set(Calendar.DAY_OF_MONTH, 1)
            scheduledTime.set(Calendar.HOUR_OF_DAY, 8)
            scheduledTime.set(Calendar.MINUTE, 0)
        }
        scheduledTime.add(periodUnit, periodAmount)
        return scheduledTime.time
    }

    private fun getNextFailedDate(date: Date) : Date {
        val scheduledTime = GregorianCalendar()
        scheduledTime.time = date
        scheduledTime.add(failedPeriodUnit, failedPeriodAmount)
        return scheduledTime.time
    }

    private fun makePayment(status: InvoiceStatus) {
        val invoices = invoiceService.fetchOfStatus(status)
        for (invoice in invoices) {
            if (invoice.status == status) {
                val result = paymentProvider.charge(invoice)
                handleChargeResult(invoice, result)
            }
        }
        //TODO handle errors
    }

    private fun handleChargeResult(invoice: Invoice, result: Boolean) {
        var invoiceToSave = invoice.copy()
        if (result) {
            invoiceToSave = invoice.copy(status = InvoiceStatus.PAID)
        } else {
            when (invoice.status) {
                InvoiceStatus.PENDING -> invoiceToSave = invoice.copy(status = InvoiceStatus.FAILED1)
                InvoiceStatus.FAILED1 -> invoiceToSave = invoice.copy(status = InvoiceStatus.FAILED2)
                InvoiceStatus.FAILED2 -> invoiceToSave = invoice.copy(status = InvoiceStatus.FAILED3)
                InvoiceStatus.FAILED3 -> invoiceToSave = invoice.copy(status = InvoiceStatus.MANUAL_CHECK)
                else -> {
                    //TODO illegal state
                }
            }
        }
        invoiceService.update(invoiceToSave)
    }

    //TODO logging
}