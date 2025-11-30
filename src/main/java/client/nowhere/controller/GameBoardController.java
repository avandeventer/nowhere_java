package client.nowhere.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import client.nowhere.model.GameBoard;
import client.nowhere.helper.GameBoardHelper;

@CrossOrigin(maxAge = 3600)
@RestController
public class GameBoardController {
    
    private final GameBoardHelper gameBoardHelper;

    @Autowired
    public GameBoardController(GameBoardHelper gameBoardHelper) {
        this.gameBoardHelper = gameBoardHelper;
    }

    @GetMapping("/game-board")
    @ResponseBody
    public GameBoard get(@RequestParam String gameCode) {
        return this.gameBoardHelper.getGameBoard(gameCode);
    }
}
