/**Arduino_DualWorldMemory.ino
 * Last Modified: 10/02/2015 (stable)
 **/

#include <Wire.h>
#include "Max3421e.h"
#include "Usbhost.h"
#include "AndroidAccessory.h"
#include "math.h"
//#include “spi.h”
//#include “adb.h”

//Musical Note Definitions
#define Note_A 0
#define Note_A_sharp 1
#define Note_B 2
#define Note_C 3
#define Note_C_sharp 4
#define Note_D 5
#define Note_D_sharp 6
#define Note_E 7
#define Note_E 8
#define Note_F 9
#define Note_G 10
#define Note_G_sharp 11

//Important Pins
#define WHITE_LED 7
#define GREEN_LED 8
#define BUZZ_PWR 3
#define BUTTON_READ 11
#define YELLOW_LED 4

//Game Timing Parameters etc.
#define GAME_OVER 80
#define alarmtime 3000
#define switchtime 5000
#define turn_duration 3000

boolean whichPlayer, playerChanged;
int buttonValue, lastButtonValue, winner;
unsigned long turnstart;

//TEMPORARY VARIABLES
boolean tryWrite = true;
uint8_t testWriteContent = 22;
//END TEMPORARY VARIABLES

AndroidAccessory acc("Manufacturer",
    "Model",
    "Description",
    "1.0",
    "http://yoursite.com",
                "0000000012345678");

uint8_t playerID;

void setup(){
    //Set serial baud rate
    Serial.begin(115200);

    pinMode(GREEN_LED, OUTPUT); //WHITE (P1)
    pinMode(WHITE_LED, OUTPUT); //RED (P2)
    pinMode(BUZZ_PWR, OUTPUT); //Supply to the Pushbutton
    pinMode(BUTTON_READ, INPUT); //Value of button press
    pinMode(YELLOW_LED, OUTPUT); //Value of button press

    playerID = 35; //Initialize with Player A's identity

    gameStart();

    Serial.print("\r\nStart");
    boolean out = acc.isConnected();
    //Serial.println(out, DEC);
    acc.powerOn();


}

void loop(){
  
    byte msg[1];

    if (acc.isConnected()) {
        Serial.println("Checkpoint A");
        
        int len;

        len = acc.read(msg, sizeof(msg), 1); // read data into msg variable
        delay(10);

        if (len > 0) { //message array: 
                      //1.new game=101 
                      //2.winner? A=35, B=67 
                      //3.read receipt=99   
                      //pass: 1.player changing? A=35, B=67; 
                      //when game is over, first pass winner, wait, then pass game over or just pass winner
            if (msg[0] == 101){ // compare received data
                gameStart();
            }

            if (msg[0] == 35 || msg[0] == 67){
                //winner=msg[0];
                gameConclude(msg[0]);
            }

            if (msg[0] == 99){
                //gameConclude(winner);
            }

            Serial.println("Data:");
            Serial.print(msg[0]); //TODO: for debugging only print out certain  messages
        }

        Serial.println("Checkpoint B");

        delay(10);
        Serial.println("Player Changed value = ");
        Serial.print(playerChanged);

        //TEST
          /*
        if(tryWrite){
          testWriteContent--;
          acc.write(testWriteContent);
          testWriteContent++;
          acc.write(testWriteContent);
          
          //tryWrite = false;
        }
        */
        //END TEST
        
        
        while (playerChanged){
            len = acc.read(msg, sizeof(msg), 1);

            if (len > 0) {
                if (msg[0] == 99){
                  break;
                }
            }
            acc.write(playerID);
        }
        
        
        Serial.println("Checkpoint C");
        
        buttonValue = digitalRead(BUTTON_READ);
        checkChangeLED();

        Serial.println("Checkpoint D");
        lastButtonValue = buttonValue;
        delay(10);

    }

    else{ //tablet not connected
        digitalWrite(GREEN_LED , HIGH); // turn off lights
        digitalWrite(WHITE_LED , HIGH);
        digitalWrite(YELLOW_LED , HIGH);
        delay(10);
        digitalWrite(GREEN_LED , LOW); // turn off lights
        digitalWrite(WHITE_LED , LOW);
        digitalWrite(YELLOW_LED , LOW);
        delay(10);
    }
    
}

void gameStart(){
    whichPlayer = false;
    buttonValue = 1;
    lastButtonValue = 1;
    turnstart = millis();
    playerChanged = 0;
    playerID = 35;

    digitalWrite(GREEN_LED, HIGH);
    note(BUZZ_PWR, Note_A_sharp, 55, 400);
    delay(200);
    note(BUZZ_PWR, Note_B, 55, 400);
    delay(200);
    note(BUZZ_PWR, Note_C, 55, 400);
    delay(200);
}

void checkChangeLED(){
    if( (millis() - turnstart) >= turn_duration ){
        digitalWrite(YELLOW_LED, HIGH);
        note(BUZZ_PWR, Note_D, 55, 1000);
        delay(1000);
        digitalWrite(YELLOW_LED, LOW);
        whichPlayer = !whichPlayer;
        turnstart = millis();
        playerChanged = 1;
        //HERE pass message to android that player changed ***************************
    }

    else if( buttonValue && !lastButtonValue ){
        tone(BUZZ_PWR, 120, 100);
        whichPlayer = !whichPlayer;
        turnstart = millis();
        playerChanged = 1;
        //HERE pass message to android that player changed *************************
    }

    else{
        playerChanged = 0;          
    }

    if(whichPlayer){ //Green = p1, White = p2 (whichPlayer = true for p1)
        digitalWrite(WHITE_LED, HIGH);
        digitalWrite(GREEN_LED, LOW);
        
        playerID = 35;
    }

    else{
        digitalWrite(GREEN_LED, HIGH);
        digitalWrite(WHITE_LED, LOW);
        
        playerID = 67;
    }
}

void gameConclude(int winner){

    if(winner == 35){ //Green = p1, White = p2
        digitalWrite(WHITE_LED, HIGH);
        digitalWrite(GREEN_LED, LOW);
    }

   else{
        digitalWrite(GREEN_LED, HIGH);
        digitalWrite(WHITE_LED, LOW);
    }

    delay(3000);
    tone(BUZZ_PWR, 200, 200);
    delay(100);
    tone(BUZZ_PWR, 100, 200);
    delay(100);
    tone(BUZZ_PWR, 300, 200);
    delay(100);
}

void note(int buzzerPin, int numHalfSteps, int key, int duration){
    if(!key){
        key = 220;  
    }

    const float notebase = 2;
    float halfSteps = (float) numHalfSteps;
    double power = pow(2, halfSteps);

    int out_freq = round(key*power);

    tone(buzzerPin, out_freq, duration);
}


