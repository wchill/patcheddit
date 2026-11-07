package app.morphe.patches.scoresapp

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

// The "Sports Alerts / scoresapp" family. Every app is built from the same codebase
// (com.scoresapp.*) but obfuscated separately, so class and member names differ per app.
private fun scoresApp(name: String, packageName: String, vararg versions: String) = Compatibility(
    name = name,
    packageName = packageName,
    targets = versions.map { AppTarget(version = it) }
)

internal val ScoresAppCompatible = arrayOf(
    scoresApp("College Football", "com.sports.schedules.football.ncaa", "13.5.8"),
    scoresApp("Pro Football", "com.scores.schedule.stats.football.nfl", "13.5.8"),
    scoresApp("Baseball", "com.sports.schedules.scores.baseball.mlb", "13.5.8"),
    scoresApp("Pro Basketball", "com.sports.schedules.basketball.nba", "13.5.9"),
    scoresApp("College Basketball","com.sports.schedules.college.basketball.ncaa", "13.5.9"),
    scoresApp("Women's College Basketball", "com.sports.schedules.ncaaw.college.basketball.women", "13.5.9"),
    scoresApp("Women's Pro Basketball", "com.sports.schedules.scores.basketball.women.wnba", "13.5.9"),
)
