/*
 * Copyright (C) 2016 Álinson Santos Xavier <isoron@gmail.com>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.core.models

import org.isoron.uhabits.core.models.Score.Companion.compute
import java.util.*
import kotlin.math.*

class ScoreList {

    private val map = HashMap<Timestamp, Score>()

    /**
     * Returns the score for a given day. If the timestamp given happens before the first
     * repetition of the habit or after the last computed score, returns a score with value zero.
     */
    operator fun get(timestamp: Timestamp): Score {
        return map[timestamp] ?: Score(timestamp, 0.0)
    }

    /**
     * Returns the list of scores that fall within the given interval.
     *
     * There is exactly one score per day in the interval. The endpoints of the interval are
     * included. The list is ordered by timestamp (decreasing). That is, the first score
     * corresponds to the newest timestamp, and the last score corresponds to the oldest timestamp.
     */
    fun getByInterval(
            fromTimestamp: Timestamp,
            toTimestamp: Timestamp,
    ): List<Score> {
        val result: MutableList<Score> = ArrayList()
        if (fromTimestamp.isNewerThan(toTimestamp)) return result
        var current = toTimestamp
        while (!current.isOlderThan(fromTimestamp)) {
            result.add(get(current))
            current = current.minus(1)
        }
        return result
    }

    /**
     * Recomputes all scores between the provided [from] and [to] timestamps.
     */
    fun recompute(
            frequency: Frequency,
            isNumerical: Boolean,
            targetValue: Double,
            computedEntries: EntryList,
            from: Timestamp,
            to: Timestamp,
    ) {
        map.clear()
        if (computedEntries.getKnown().isEmpty()) return
        if (from.isNewerThan(to)) return
        var rollingSum = 0.0
        var numerator = frequency.numerator
        var denominator = frequency.denominator
        val freq = frequency.toDouble()
        val values = computedEntries.getByInterval(from, to).map { it.value }.toIntArray()

        // For non-daily boolean habits, we double the numerator and the denominator to smooth
        // out irregular repetition schedules (for example, weekly habits performed on different
        // days of the week)
        if (!isNumerical && freq < 1.0) {
            numerator *= 2
            denominator *= 2
        }

        var previousValue = 0.0
        for (i in values.indices) {
            val offset = values.size - i - 1
            if (isNumerical) {
                rollingSum += values[offset]
                if (offset + denominator < values.size) {
                    rollingSum -= values[offset + denominator]
                }
                val percentageCompleted = min(1.0, rollingSum / 1000 / targetValue)
                previousValue = compute(freq, previousValue, percentageCompleted)
            } else {
                if (values[offset] == Entry.YES_MANUAL) {
                    rollingSum += 1.0
                }
                if (offset + denominator < values.size) {
                    if (values[offset + denominator] == Entry.YES_MANUAL) {
                        rollingSum -= 1.0
                    }
                }
                if (values[offset] != Entry.SKIP) {
                    val percentageCompleted = Math.min(1.0, rollingSum / numerator)
                    previousValue = compute(freq, previousValue, percentageCompleted)
                }
            }
            val timestamp = from.plus(i)
            map[timestamp] = Score(timestamp, previousValue)
        }
    }
}