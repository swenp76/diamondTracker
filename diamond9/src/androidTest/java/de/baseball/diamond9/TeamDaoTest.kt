package de.baseball.diamond9

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.baseball.diamond9.db.AppDatabase
import de.baseball.diamond9.db.TeamDao
import de.baseball.diamond9.db.TeamPosition
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TeamDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var teamDao: TeamDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        teamDao = db.teamDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertTeam_and_getAllTeams_returnsTeam() {
        teamDao.insertTeam(Team(name = "Mets"))

        val teams = teamDao.getAllTeams()
        assertEquals(1, teams.size)
        assertEquals("Mets", teams[0].name)
    }

    @Test
    fun getAllTeams_returnsSortedByName() {
        teamDao.insertTeam(Team(name = "Tigers"))
        teamDao.insertTeam(Team(name = "Bears"))
        teamDao.insertTeam(Team(name = "Aces"))

        val teams = teamDao.getAllTeams()
        assertEquals("Aces", teams[0].name)
        assertEquals("Bears", teams[1].name)
        assertEquals("Tigers", teams[2].name)
    }

    @Test
    fun updateTeamName_changesName() {
        val id = teamDao.insertTeam(Team(name = "OldName"))
        teamDao.updateTeamName(id, "NewName")

        val teams = teamDao.getAllTeams()
        assertEquals("NewName", teams[0].name)
    }

    @Test
    fun deleteTeam_removesTeamAndPositionsAndPlayers() {
        val teamId = teamDao.insertTeam(Team(name = "Mets"))
        teamDao.insertTeamPosition(TeamPosition(teamId = teamId, position = 1))
        teamDao.insertTeamPosition(TeamPosition(teamId = teamId, position = 2))
        db.playerDao().insertPlayer(Player(teamId = teamId, name = "Schmidt", number = "7",
            primaryPosition = 1, secondaryPosition = 0, isPitcher = true, birthYear = 2000))

        teamDao.deleteTeam(teamId)

        assertTrue(teamDao.getAllTeams().isEmpty())
        assertTrue(teamDao.getEnabledPositions(teamId).isEmpty())
        assertTrue(db.playerDao().getPlayersForTeam(teamId).isEmpty())
    }

    @Test
    fun getEnabledPositions_returnsInsertedPositions() {
        val teamId = teamDao.insertTeam(Team(name = "Mets"))
        teamDao.insertTeamPosition(TeamPosition(teamId = teamId, position = 1))
        teamDao.insertTeamPosition(TeamPosition(teamId = teamId, position = 5))
        teamDao.insertTeamPosition(TeamPosition(teamId = teamId, position = 10))

        val positions = teamDao.getEnabledPositions(teamId)
        assertEquals(setOf(1, 5, 10), positions)
    }

    @Test
    fun insertTeamPosition_withDuplicate_doesNotThrow() {
        val teamId = teamDao.insertTeam(Team(name = "Mets"))
        teamDao.insertTeamPosition(TeamPosition(teamId = teamId, position = 3))
        teamDao.insertTeamPosition(TeamPosition(teamId = teamId, position = 3)) // duplicate → IGNORE

        assertEquals(1, teamDao.getEnabledPositions(teamId).size)
    }

    @Test
    fun deleteTeamPosition_removesOnlyThatPosition() {
        val teamId = teamDao.insertTeam(Team(name = "Mets"))
        teamDao.insertTeamPosition(TeamPosition(teamId = teamId, position = 1))
        teamDao.insertTeamPosition(TeamPosition(teamId = teamId, position = 2))

        teamDao.deleteTeamPosition(teamId, 1)

        assertEquals(setOf(2), teamDao.getEnabledPositions(teamId))
    }
}
