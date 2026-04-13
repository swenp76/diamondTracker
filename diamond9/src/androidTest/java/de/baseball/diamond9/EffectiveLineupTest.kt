package de.baseball.diamond9

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.baseball.diamond9.db.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the getEffectiveLineup() logic in DatabaseHelper:
 * starts from the raw own_lineup table and overlays substitutions so that
 * each slot returns the *current* player (last substitute in, if any).
 *
 * Uses in-memory Room + DAOs directly, replicating the logic under test so the
 * SQL data model and Kotlin composition are both exercised.
 */
@RunWith(AndroidJUnit4::class)
class EffectiveLineupTest {

    private lateinit var db: AppDatabase
    private lateinit var teamDao: TeamDao
    private lateinit var playerDao: PlayerDao
    private lateinit var gameDao: GameDao
    private lateinit var lineupDao: LineupDao

    private var teamId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        teamDao = db.teamDao()
        playerDao = db.playerDao()
        gameDao = db.gameDao()
        lineupDao = db.lineupDao()
        teamId = teamDao.insertTeam(Team(name = "Cardinals"))
    }

    @After
    fun tearDown() { db.close() }

    private fun newPlayer(name: String, number: String = "0"): Long =
        playerDao.insertPlayer(Player(teamId = teamId, name = name, number = number, primaryPosition = 3))

    private fun newGame(): Long =
        gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))

    private fun setSlot(gameId: Long, slot: Int, playerId: Long) =
        lineupDao.setOwnLineupPlayer(OwnLineupSlot(gameId = gameId, slot = slot, playerId = playerId))

    private fun addSub(gameId: Long, slot: Int, outId: Long, inId: Long) =
        lineupDao.insertSubstitution(Substitution(gameId = gameId, slot = slot, playerOutId = outId, playerInId = inId))

    /**
     * Mirrors DatabaseHelper.getEffectiveLineup() but uses the in-memory DAO directly.
     * This ensures both the SQL queries and the overlay logic are correct.
     */
    private fun effectiveLineup(gameId: Long): Map<Int, Player> {
        val base = lineupDao.getOwnLineupRaw(gameId)
            .associate { row ->
                row.slot to Player(
                    id = row.playerId, teamId = row.teamId, name = row.name,
                    number = row.number, primaryPosition = row.primaryPosition,
                    secondaryPosition = row.secondaryPosition, isPitcher = row.isPitcher,
                    birthYear = row.birthYear
                )
            }
            .toMutableMap()
        lineupDao.getSubstitutionsForGame(gameId)
            .groupBy { it.slot }
            .forEach { (slot, slotSubs) ->
                val lastSub = slotSubs.last()
                val player = playerDao.getPlayerById(lastSub.playerInId)
                if (player != null) base[slot] = player
            }
        return base
    }

    // ── No lineup ─────────────────────────────────────────────────────────────

    @Test
    fun effectiveLineup_emptyGame_returnsEmpty() {
        val gameId = newGame()
        assertTrue(effectiveLineup(gameId).isEmpty())
    }

    // ── No substitutions ──────────────────────────────────────────────────────

    @Test
    fun effectiveLineup_noSubstitutions_sameAsBaseLineup() {
        val gameId = newGame()
        val p1 = newPlayer("Müller", "7")
        val p2 = newPlayer("Schmidt", "9")
        val p3 = newPlayer("Weber", "4")
        setSlot(gameId, 1, p1)
        setSlot(gameId, 2, p2)
        setSlot(gameId, 3, p3)

        val lineup = effectiveLineup(gameId)
        assertEquals(3, lineup.size)
        assertEquals(p1, lineup[1]?.id)
        assertEquals(p2, lineup[2]?.id)
        assertEquals(p3, lineup[3]?.id)
    }

    // ── Single substitution ───────────────────────────────────────────────────

    @Test
    fun effectiveLineup_singleSub_replacesSlot() {
        val gameId = newGame()
        val starter = newPlayer("Müller", "7")
        val sub = newPlayer("Braun", "22")
        setSlot(gameId, 1, starter)
        addSub(gameId, 1, outId = starter, inId = sub)

        val lineup = effectiveLineup(gameId)
        assertEquals(sub, lineup[1]?.id)
    }

    // ── Multiple substitutions on same slot ───────────────────────────────────

    @Test
    fun effectiveLineup_multipleSubsSameSlot_returnsLastPlayerIn() {
        val gameId = newGame()
        val starter = newPlayer("Müller", "7")
        val sub1 = newPlayer("Braun", "22")
        val sub2 = newPlayer("Koch", "33")
        setSlot(gameId, 1, starter)
        addSub(gameId, 1, outId = starter, inId = sub1)
        addSub(gameId, 1, outId = sub1, inId = sub2)

        val lineup = effectiveLineup(gameId)
        assertEquals(sub2, lineup[1]?.id)
    }

    // ── Substitution on one slot leaves other slots unchanged ─────────────────

    @Test
    fun effectiveLineup_subOnOneSlot_otherSlotsUnaffected() {
        val gameId = newGame()
        val p1 = newPlayer("Müller", "7")
        val p2 = newPlayer("Schmidt", "9")
        val subFor1 = newPlayer("Braun", "22")
        setSlot(gameId, 1, p1)
        setSlot(gameId, 2, p2)
        addSub(gameId, 1, outId = p1, inId = subFor1)

        val lineup = effectiveLineup(gameId)
        assertEquals(subFor1, lineup[1]?.id)  // slot 1 replaced
        assertEquals(p2, lineup[2]?.id)        // slot 2 untouched
    }
}
