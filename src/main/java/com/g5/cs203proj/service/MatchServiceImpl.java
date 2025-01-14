package com.g5.cs203proj.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.g5.cs203proj.DTO.MatchDTO;
import com.g5.cs203proj.entity.Match;
import com.g5.cs203proj.entity.Player;
import com.g5.cs203proj.entity.Tournament;
import com.g5.cs203proj.enums.Statuses;
import com.g5.cs203proj.exception.match.*;
import com.g5.cs203proj.exception.player.*;
import com.g5.cs203proj.exception.tournament.*;
import com.g5.cs203proj.repository.MatchRepository;
import com.g5.cs203proj.repository.TournamentRepository;

@Service
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final TournamentRepository tournamentRepository;
    private final PlayerService playerService;
    private final TournamentService tournamentService;
    private final EmailService emailService;

    @Autowired
    public MatchServiceImpl(MatchRepository matchRepository, TournamentRepository tournamentRepository,
            PlayerService playerService, TournamentService tournamentService, EmailService emailService) {
        this.matchRepository = matchRepository;
        this.tournamentRepository = tournamentRepository;
        this.playerService = playerService;
        this.tournamentService = tournamentService;
        this.emailService = emailService;
    }

    @Override
    public Match saveMatch(Match match) {
        if (match == null) {
            throw new IllegalArgumentException("Match cannot be null");
        }
        return matchRepository.save(match);
    }

    @Override
    public void deleteMatch(Long id) {
        Match match = findMatchById(id);
        if (match != null) {
            matchRepository.delete(match);
        }
    }

    @Override
    public Match findMatchById(Long id) {
        return matchRepository.findById(id).orElse(null);
    }

    @Override
    public Match assignRandomPlayers(Long matchId) {
        Match match = matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));

        if (match.getTournament() == null) {
            throw new IllegalArgumentException("Match must be associated with a Tournament.");
        }

        Long tournamentIdOfMatch = match.getTournament().getId();
        List<Player> availablePlayers = playerService.getAvailablePlayersForTournament(tournamentIdOfMatch);
        
        int playerCount = availablePlayers.size();
        if (playerCount < 2) {
            throw new PlayerRangeException(PlayerRangeException.RangeErrorType.NOT_ENOUGH_PLAYERS, "Current player count is " + playerCount);
        }
        
        Collections.shuffle(availablePlayers);
        Player p1 = availablePlayers.get(0);
        Player p2 = availablePlayers.get(1);
        match.setPlayer1(p1);
        match.setPlayer2(p2);
        matchRepository.save(match);

        p1.addMatchesAsPlayer1(match);
        p2.addMatchesAsPlayer2(match);
        playerService.savePlayer(p1);
        playerService.savePlayer(p2);

        try {
            emailService.sendMatchNotification(match);
        } catch (Exception e) {
            System.err.println("Failed to send email notification: " + e.getMessage());
        }

        return match;
    }

    @Override
    public Match reassignPlayersToMatch(Long oldMatchId, Long newMatchId) {
        Match oldMatch = findMatchById(oldMatchId);
        Match newMatch = findMatchById(newMatchId);

        if (oldMatch == null || newMatch == null) {
            throw new MatchNotFoundException(oldMatch == null ? oldMatchId : newMatchId);
        }
    
        Player p1 = oldMatch.getPlayer1();
        Player p2 = oldMatch.getPlayer2();
        
        if (p1 == null || p2 == null) {
            throw new IllegalStateException("Source match must have both players assigned");
        }

        newMatch.setPlayer1(p1);
        newMatch.setPlayer2(p2);
        matchRepository.save(newMatch);

        p1.addMatchesAsPlayer1(newMatch);
        p2.addMatchesAsPlayer2(newMatch);
        playerService.savePlayer(p1);
        playerService.savePlayer(p2);

        return newMatch;
    }

    /*
     * called whenever a match is completed. updates match details and tournament rankings based on result
     * @param: match: match object to be updated
     * @param: winner: player object who won the match
     * @param: isDraw: boolean value indicating if the match is a draw
     */

    @Override
    public void processMatchResult(Match match, Player winner, boolean isDraw) {
        if (match == null) {
            throw new IllegalArgumentException("Match cannot be null");
        }
        match.setMatchStatus(Statuses.COMPLETED.getDisplayName());
        match.setDraw(isDraw);
        match.setWinner(winner);
        if (!isDraw) {
            match.setEloChange(winner);
            tournamentService.updateTournamentRankings(match.getTournament(), match);
        }
        matchRepository.save(match);


    }

    @Override
    public List<Match> getMatchesForTournament(Tournament tournament) {
        if (tournament == null) {
            throw new IllegalArgumentException("Tournament cannot be null");
        }
        return tournament.getTournamentMatchHistory();
    }

    @Override
    public List<Match> getMatchesForPlayer(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        List<Match> matches = new ArrayList<>();
        matches.addAll(player.getMatchesAsPlayer1());
        matches.addAll(player.getMatchesAsPlayer2());
        return matches;
    }

    @Override
    public HashMap<String, Boolean> viewCheckedInStatus(Match match) {
        if (match == null || match.getPlayer1() == null || match.getPlayer2() == null) {
            throw new IllegalArgumentException("Match and both players must be non-null");
        }

        HashMap<String, Boolean> checkInStatuses = new HashMap<>();
        checkInStatuses.put(match.getPlayer1().getUsername(), match.getStatusP1());
        checkInStatuses.put(match.getPlayer2().getUsername(), match.getStatusP2());
        return checkInStatuses;
    }
    
    public boolean bothPlayersCheckedIn(Match match) {
        if (match == null) {
            throw new IllegalArgumentException("Match cannot be null");
        }
        return match.getStatusP1() && match.getStatusP2();
    }

    @Override
    public MatchDTO convertToDTO(Match match) {
        if (match == null) {
            throw new IllegalArgumentException("Match cannot be null");
        }

        MatchDTO matchDTO = new MatchDTO();
        matchDTO.setId(match.getMatchId());
        matchDTO.setPlayer1Id(match.getPlayer1() != null ? match.getPlayer1().getId() : null);
        matchDTO.setPlayer2Id(match.getPlayer2() != null ? match.getPlayer2().getId() : null);
        matchDTO.setTournamentId(match.getTournament().getId());
        matchDTO.setStatusP1(match.getStatusP1());
        matchDTO.setStatusP2(match.getStatusP2());
        matchDTO.setWinnerId(match.getWinner() != null ? match.getWinner().getId() : null);
        matchDTO.setDraw(match.getDraw());
        matchDTO.setMatchStatus(match.getMatchStatus());
        matchDTO.setEloChange(match.getEloChange());
        return matchDTO;
    }

    @Override
    public Match convertToEntity(MatchDTO matchDTO) {
        if (matchDTO == null) {
            throw new IllegalArgumentException("MatchDTO cannot be null");
        }

        Match match = new Match();
        match.setPlayer1(playerService.getPlayerById(matchDTO.getPlayer1Id()));
        match.setPlayer2(playerService.getPlayerById(matchDTO.getPlayer2Id()));
        match.setTournament(tournamentService.getTournamentById(matchDTO.getTournamentId()));
        match.setStatusP1(matchDTO.isStatusP1());
        match.setStatusP2(matchDTO.isStatusP2());
        match.setWinner(matchDTO.getWinnerId() != null ? playerService.getPlayerById(matchDTO.getWinnerId()) : null);
        match.setDraw(matchDTO.isDraw());
        match.setMatchStatus(matchDTO.getMatchStatus());    
        match.setOnlyEloChange(matchDTO.getEloChange());
        return match;
    }

    @Override
    public List<Match> createRoundRobinMatches(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
            .orElseThrow(() -> new TournamentNotFoundException(tournamentId));
        
        List<Player> players = playerService.getAvailablePlayersForTournament(tournamentId);
        
        if (players.size() > 16) {
            throw new PlayerRangeException(PlayerRangeException.RangeErrorType.TOO_MANY_PLAYERS, 
                "The tournament currently has " + players.size() + " players. The maximum allowed for a round-robin format is 16.");
        }

        List<Match> matches = new ArrayList<>();
        int totalPlayers = players.size();

        for (int i = 0; i < totalPlayers; i++) {
            for (int j = i + 1; j < totalPlayers; j++) {
                Match match = new Match();
                match.setPlayer1(players.get(i));
                match.setPlayer2(players.get(j));
                match.setTournament(tournament);
                Match savedMatch = matchRepository.save(match);
                matches.add(savedMatch);

                try {
                    emailService.sendMatchNotification(savedMatch);
                } catch (Exception e) {
                    System.err.println("Failed to send email notification for match: " + savedMatch.getMatchId() + " - " + e.getMessage());
                }
            }
        }

        tournament.getTournamentMatchHistory().addAll(matches);
        tournamentRepository.save(tournament);

        return matches;
    }

    @Override
    public List<Match> createSingleEliminationMatches(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
            .orElseThrow(() -> new TournamentNotFoundException(tournamentId));
        
        List<Player> players = playerService.getAvailablePlayersForTournament(tournamentId);
        List<Match> matches = new ArrayList<>();

        if (players.isEmpty()) {
            return matches;
        }

        // Create first round matches
        int totalPlayers = players.size();
        int firstRoundMatches = totalPlayers / 2;

        // Create first round matches with assigned players
        for (int i = 0; i < firstRoundMatches; i++) {
            Match match = new Match();
            match.setPlayer1(players.get(i * 2));
            match.setPlayer2(players.get(i * 2 + 1));
            match.setTournament(tournament);
            Match savedMatch = matchRepository.save(match);
            matches.add(savedMatch);

            try {
                emailService.sendMatchNotification(savedMatch);
            } catch (Exception e) {
                System.err.println("Failed to send email notification for match: " + savedMatch.getMatchId() + " - " + e.getMessage());
            }
        }

        // If there's an odd number of players, create a bye match for the last player
        if (totalPlayers % 2 != 0 && totalPlayers > 2) {
            Match byeMatch = new Match();
            byeMatch.setPlayer1(players.get(totalPlayers - 1));
            byeMatch.setTournament(tournament);
            Match savedMatch = matchRepository.save(byeMatch);
            matches.add(savedMatch);
        }

        // Create subsequent round matches (without players assigned yet)
        int remainingMatches = firstRoundMatches / 2;
        while (remainingMatches > 0) {
            for (int i = 0; i < remainingMatches; i++) {
                Match match = new Match();
                match.setTournament(tournament);
                matches.add(matchRepository.save(match));
            }
            remainingMatches = remainingMatches / 2;
        }

        tournament.getTournamentMatchHistory().addAll(matches);
        tournamentRepository.save(tournament);

        return matches;
    }

    // @Override
    // public boolean isPowerOfTwo(int numPlayers) {
    //     if (numPlayers <= 0) {
    //         return false;
    //     }
        
    //     while (numPlayers > 1) {
    //         if (numPlayers % 2 != 0) {
    //             return false;
    //         }
    //         numPlayers /= 2;
    //     }
        
    //     return true;
    // }
}
