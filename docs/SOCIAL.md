# Parties, clans and character level

Open the existing character/training menu. The new **Party** and **Clans** tabs are beside Styles. The header shows the mod's current character level beside TP on every menu page. It uses the existing attribute-based level calculation, rather than Minecraft XP.

## Party

Create a party, type an online player's username and send an invitation. The invited player accepts or declines in their Party tab. Parties hold up to eight members. Invitations expire after two minutes of server time. The leader can remove members, transfer leadership or disband the party. Leaving as leader passes leadership to the next member; an empty group is deleted. Party membership survives reconnects and world saves.

## Clans

Create a clan with a unique name of 3-24 letters, numbers, spaces, underscores or hyphens. A player may belong to one clan and one party simultaneously. Clans hold up to 32 members.

Clan leaders choose **Open** or **Invite-only** joining. New clans start open. Players can browse clans and join open ones, or accept a leader's invitation. Clan leaders can invite, remove members, transfer leadership, leave or disband. Membership is stored by UUID, and the roster includes offline members.

Members display as `Username [Clan Name]`, including overhead names and the player list. Name suffixes refresh when membership changes. Earned titles and fusion names are retained by appending the clan after their name formatting handlers.

## Storage and validation

Groups are stored in the world's overworld SavedData file `data/dbzenith_social.dat`. Pending invitations are temporary and are cleared on server restart. The server checks leadership, membership, invitation expiry, name uniqueness and group capacities. Clients receive their own group/invite data and a public clan directory. Both server and clients need the updated mod.

Three GameTests cover invitation acceptance and rejection, unauthorized actions, leadership transfer, open/private clan joining, duplicate/invalid names, save/load persistence, suffix refresh, earned-title compatibility and expired/declined invites. The isolated client review checks tabs, level/TP labels, empty and populated views, joining-mode changes and a paginated roster at GUI scales 3 and 4. A live remote multiplayer session has not been tested.

The source checkout is older than the installed 0.88.0 baseline. Use `tools/PackageHairUpdate.ps1` to add the allowlisted hair, Nimbus and social classes to the original 0.88.0 JAR. The social tabs attach through screen events; the newer baseline menu, registries and existing network channel are not replaced. Development review and GameTest classes are excluded from the release.
