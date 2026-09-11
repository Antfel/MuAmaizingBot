package com.example.muamaizingbot.bot.devilsquare

/**
 * Session memory for Devil Square. Cleared on bot startup ([resetSession]).
 */
object DevilSquareState {

    enum class Phase {
        IDLE,
        PREP_PET,
        PREP_ELF,
        PREP_POTIONS,
        OPEN_DAILY_GOAL,
        WAIT_C5,
        INSIDE_SETUP,
        IN_EVENT,
    }

    @Volatile
    var phase: Phase = Phase.IDLE
        private set

    @Volatile
    var processActive: Boolean = false
        private set

    /** No more Daily Goal / C5 until 06:00 Lima or bot restart. */
    @Volatile
    var dayOff: Boolean = false
        private set

    @Volatile
    var dayOffAccessDay: String? = null
        private set

    @Volatile
    var slotConsumedHour: String? = null
        private set

    /** ignore_clock: one attempt this bot session. */
    @Volatile
    var testAttemptDone: Boolean = false
        private set

    @Volatile
    var remainingAtEnter: Int? = null

    @Volatile
    var diamondDone: Boolean = false

    @Volatile
    var autoDone: Boolean = false

    fun resetSession() {
        phase = Phase.IDLE
        processActive = false
        dayOff = false
        dayOffAccessDay = null
        slotConsumedHour = null
        testAttemptDone = false
        remainingAtEnter = null
        diamondDone = false
        autoDone = false
    }

    fun clearDayOffIfNewAccessDay() {
        val today = DevilSquareClock.accessDayKey()
        if (dayOff && dayOffAccessDay != null && dayOffAccessDay != today) {
            dayOff = false
            dayOffAccessDay = null
        }
    }

    fun beginProcess() {
        processActive = true
        phase = Phase.PREP_PET
        remainingAtEnter = null
        diamondDone = false
        autoDone = false
    }

    fun setPhase(next: Phase) {
        phase = next
    }

    fun markDayOff() {
        dayOff = true
        dayOffAccessDay = DevilSquareClock.accessDayKey()
        finishAttempt(consumeSlot = true)
    }

    fun finishAttempt(consumeSlot: Boolean) {
        processActive = false
        phase = Phase.IDLE
        diamondDone = false
        autoDone = false
        if (consumeSlot) {
            slotConsumedHour = DevilSquareClock.evenHourKey()
            testAttemptDone = true
        }
    }

    fun isHoldingPriority(): Boolean = processActive

    fun isInEvent(): Boolean = phase == Phase.IN_EVENT || phase == Phase.INSIDE_SETUP
}
