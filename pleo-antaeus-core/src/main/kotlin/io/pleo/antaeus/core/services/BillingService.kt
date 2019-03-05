package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
import java.lang.Exception
import java.util.*
import kotlin.concurrent.schedule

private val logger = KotlinLogging.logger {}

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

    private fun getNextPendingDate(date: Date): Date {
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

    private fun getNextFailedDate(date: Date): Date {
        val scheduledTime = GregorianCalendar()
        scheduledTime.time = date
        scheduledTime.add(failedPeriodUnit, failedPeriodAmount)
        return scheduledTime.time
    }

    private fun makePayment(status: InvoiceStatus) {
        val invoices = invoiceService.fetchOfStatus(status)
        for (invoice in invoices) {
            var result = false
            var paymentException: Exception? = null
            try {
                result = paymentProvider.charge(invoice)
            } catch (e: Exception) {
                logger.debug("Failed to charge client due to Exception", e)
                paymentException = e
            } finally {
                handleChargeResult(invoice, result, paymentException)
            }
        }
    }

    private fun handleChargeResult(invoice: Invoice, result: Boolean, exception: Exception?) {
        var invoiceToSave = invoice.copy()

        if (result && exception == null) {
            invoiceToSave = invoice.copy(status = InvoiceStatus.PAID)
        } else if (exception != null) {
            when (exception) {
                is CurrencyMismatchException -> invoiceToSave = invoice.copy(status = InvoiceStatus.MANUAL_CHECK)
                is CustomerNotFoundException -> invoiceToSave = invoice.copy(status = InvoiceStatus.MANUAL_CHECK)
                is NetworkException -> invoiceToSave = handleFalsePaymentProviderResponse(invoice)
            }
        } else {
            invoiceToSave = handleFalsePaymentProviderResponse(invoice)
        }

        invoiceService.update(invoiceToSave)
    }

    private fun handleFalsePaymentProviderResponse(invoice: Invoice): Invoice {
        var invoiceToReturn = invoice.copy()
        when (invoice.status) {
            InvoiceStatus.PENDING -> invoiceToReturn = invoice.copy(status = InvoiceStatus.FAILED1)
            InvoiceStatus.FAILED1 -> invoiceToReturn = invoice.copy(status = InvoiceStatus.FAILED2)
            InvoiceStatus.FAILED2 -> invoiceToReturn = invoice.copy(status = InvoiceStatus.FAILED3)
            InvoiceStatus.FAILED3 -> invoiceToReturn = invoice.copy(status = InvoiceStatus.MANUAL_CHECK)
            else -> {
                logger.warn("Illegal state of invoice")
            }
        }
        return invoiceToReturn
    }

    //TODO logging
}