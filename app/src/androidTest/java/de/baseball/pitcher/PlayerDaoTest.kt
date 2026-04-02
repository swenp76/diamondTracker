package de.baseball.pitcher

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.baseball.pitcher.db.AppDatabase
import de.baseball.pitcher.db.PlayerDao
import de.baseball.pitcher.db.TeamDao
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var playerDao: PlayerDao
    private lateinit var teamDao: TeamDao

    private var teamId: Long = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playerDao = db.playerDao()
        teamDao = db.teamDao()
        teamId = teamDao.insertTeam(Team(name = "Mets"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun player(name: String, number: String = "0", pos: Int = 1) =
        Player(teamId = teamId, name = name, number = number,
               primaryPosition = pos, secondaryPosition = 0,
               isPitcher = pos == 1, birthYear = 2000)

    @Test
    fun insertPlayer_and_getPlayersForTeam_returnsPlayer() {
        playerDao.insertPlayer(player("Schmidt", "7"))

        val players = playerDao.getPlayersForTeam(teamId)
        assertEquals(1, players.size)
        assertEquals("Schmidt", players[0].name)
        assertEquals("7", players[0].number)
    }

    @Test
    fun getPlayersForTeam_sortedByNumberThenName() {
        playerDao.insertPlayer(player("Brauer", "10"))
        playerDao.insertPlayer(player("Zander", "2"))
        playerDao.insertPlayer(player("Alpha",  "2"))

        val players = playerDao.getPlayersForTeam(teamId)
        // number ASC: "10" sorts before "2" lexicographically? No – Room sorts as text.
        // "10" < "2" lexicographically, so Alpha/Zander (2) come AFTER Brauer (10) if text-sorted.
        // Actual ORDER BY number ASC, name ASC with text: "10" < "2" → Brauer first
        assertEquals("Brauer", players[0].name)
        assertEquals("Alpha",  players[1].name)
        assertEquals("Zander", players[2].name)
    }

    @Test
    fun getPlayerById_returnsCorrectPlayer() {
        val id = playerDao.insertPlayer(player("Schmidt", "7"))
        val p = playerDao.getPlayerById(id)
        assertNotNull(p)
        assertEquals("Schmidt", p!!.name)
    }

    @Test
    fun getPlayerById_returnsNull_whenNotFound() {
        assertNull(playerDao.getPlayerById(999L))
    }

    @Test
    fun updatePlayer_changesFields() {
        val id = playerDao.insertPlayer(player("OldName", "5", pos = 3))
        val updated = Player(id = id, teamId = teamId, name = "NewName", number = "9",
                             primaryPosition = 1, secondaryPosition = 6,
                             isPitcher = true, birthYear = 2001)

        playerDao.updatePlayer(updated)

        val p = playerDao.getPlayerById(id)!!
        assertEquals("NewName", p.name)
        assertEquals("9", p.number)
        assertEquals(1, p.primaryPosition)
        assertEquals(6, p.secondaryPosition)
        assertTrue(p.isPitcher)
        assertEquals(2001, p.birthYear)
    }

    @Test
    fun deletePlayer_removesPlayer() {
        val id = playerDao.insertPlayer(player("Schmidt"))
        playerDao.deletePlayer(id)
        assertTrue(playerDao.getPlayersForTeam(teamId).isEmpty())
    }

    @Test
    fun deletePlayer_doesNotAffectOtherPlayers() {
        val id1 = playerDao.insertPlayer(player("Schmidt", "7"))
        playerDao.insertPlayer(player("Müller", "8"))

        playerDao.deletePlayer(id1)

        val remaining = playerDao.getPlayersForTeam(teamId)
        assertEquals(1, remaining.size)
        assertEquals("Müller", remaining[0].name)
    }

    @Test
    fun getPlayersForTeam_doesNotReturnPlayersFromOtherTeams() {
        val otherTeamId = teamDao.insertTeam(Team(name = "Yankees"))
        playerDao.insertPlayer(player("Schmidt"))
        playerDao.insertPlayer(
            Player(teamId = otherTeamId, name = "Jones", number = "1",
                   primaryPosition = 1, secondaryPosition = 0, isPitcher = true, birthYear = 1999)
        )

        val players = playerDao.getPlayersForTeam(teamId)
        assertEquals(1, players.size)
        assertEquals("Schmidt", players[0].name)
    }
}
