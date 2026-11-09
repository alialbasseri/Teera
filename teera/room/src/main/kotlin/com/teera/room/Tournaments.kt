package com.teera.room

import com.teera.core.Tournament
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** Tournaments the server knows, kept in the books so a restart does not lose a bracket. */
class Tournaments(private val records: Records) {
    private val known = ConcurrentHashMap<String, Tournament>()

    fun newId(): String {
        val letters = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        while (true) {
            val id = "T" + (1..4).map { letters[Random.nextInt(letters.length)] }.joinToString("")
            if (!known.containsKey(id)) return id
        }
    }

    suspend fun get(id: String): Tournament? {
        val key = id.trim().uppercase()
        return known[key] ?: records.loadTournament(key)?.also { known[key] = it }
    }

    fun put(tournament: Tournament) {
        known[tournament.id] = tournament
        records.saveTournament(tournament)
    }

    /** A tournament match's room finished: the team that did not lose goes through. */
    fun matchFinished(id: String, round: Int, index: Int, losingSide: Int) {
        val t = known[id] ?: return
        val match = t.rounds.getOrNull(round)?.getOrNull(index) ?: return
        val winner = (if (losingSide == 0) match.b else match.a) ?: return
        t.withWinner(round, index, winner)?.let(::put)
    }
}
