package lila
package team

import lila.user.{ Me, User, UserRepo }
import lila.security.Granter
import lila.memo.CacheApi.*
import cats.derived.*

object TeamSecurity:
  enum Permission(val desc: String) derives Eq:
    case Public   extends Permission("Visible as leader on the team page")
    case Settings extends Permission("Manage the team settings")
    case Tour     extends Permission("Create, manage and join team tournaments")
    case Comm     extends Permission("Moderate the forum and chats")
    case Request  extends Permission("Accept and decline join requests")
    case PmAll    extends Permission("Send private messages to all members")
    case Kick     extends Permission("Kick members of the team")
    case Admin    extends Permission("Manage leader permissions")
    def key = toString.toLowerCase
  object Permission:
    type Selector = Permission.type => Permission
    val byKey = values.mapBy(_.key)

  case class LeaderData(name: UserStr, perms: Set[Permission])
  def tableStr(data: Seq[LeaderData]) =
    data.map(d => s"${d.name} (${d.perms.map(_.key).mkString(" ")})").mkString("\n")

final class TeamSecurity(teamRepo: TeamRepo, memberRepo: MemberRepo, userRepo: UserRepo, cached: Cached)(using
    Executor
):

  import TeamSecurity.*

  def setLeaders(team: Team.WithLeaders, data: Seq[LeaderData])(using by: Me): Funit = for
    _ <- memberRepo.unsetAllPerms(team.id)
    _ <- memberRepo.setAllPerms(team.id, data)
  yield
    team.leaders.map(_.user) foreach cached.nbRequests.invalidate
    data.map(_.name.id) foreach cached.nbRequests.invalidate
    logger.info(s"valid setLeaders ${team.id} by ${by.userId}: ${tableStr(data)}")

  object form:
    import play.api.data.*
    import play.api.data.Forms.*

    private val leaderForm = mapping(
      "name" -> lila.user.UserForm.historicalUsernameField,
      "perms" -> seq(nonEmptyText)
        .transform[Set[Permission]](
          _.flatMap(Permission.byKey.get).toSet,
          _.toSeq.map(_.key)
        )
    )(LeaderData.apply)(unapply)

    def leaders(t: Team.WithLeaders)(using me: Me): Form[Seq[LeaderData]] = Form(
      single("leaders" -> seq(leaderForm))
        .verifying(
          "You are not allowed to change permissions",
          _ => t.leaders.exists(l => l.is(me) && l.perms(Permission.Admin))
        )
        .verifying(
          "You can't make Lichess a leader",
          _.exists(_.name is User.lichessId) &&
            !t.leaders.exists(_ is User.lichessId) &&
            !Granter(_.ManageTeam)
        )
        .verifying(
          "There must be at least one leader able to manage permissions",
          _.exists(_.perms(Permission.Admin))
        )
        .verifying(
          s"No more than ${Team.maxLeaders} leaders, please",
          _.sizeIs <= Team.maxLeaders
        )
        .verifying(
          "Duplicated leader name",
          d => d.map(_.name).distinct.sizeIs == d.size
        )
        .verifying(
          "You cannot evict the team creator",
          d =>
            Granter(_.ManageTeam) || !t.hasAdminCreator || d.exists: l =>
              l.name.is(t.createdBy) && l.perms(Permission.Admin)
        )
        .verifying(
          "Kid accounts cannot be manage permissions",
          d =>
            userRepo
              .filterKid(d.filter(_.perms(Permission.Admin)).map(_.name))
              .await(1.second, "team leader kids")
              .isEmpty
        )
    ).fill(t.leaders.map(m => LeaderData(m.user into UserStr, m.perms)))
