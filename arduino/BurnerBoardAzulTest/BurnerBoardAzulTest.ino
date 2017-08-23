

#include <OctoWS2811.h>
#include <CmdMessenger.h>


// Burner Boards have 2 relay outputs, speed-pin control and a spare aux relay
#define SPEED_PIN 23
#define AUX_PIN 22


//#define DEBUG_PIXELS 1

#define RGB_MAX 255

const int ledsPerStrip = 552;
const int NUM_LEDS = 4416;

DMAMEM int displayMemory[ledsPerStrip*6];
int drawingMemory[ledsPerStrip*6];

const int config = WS2811_GRB | WS2811_800kHz;
OctoWS2811 leds(ledsPerStrip, displayMemory, drawingMemory, config);
bool inited = false;


char field_separator   = ',';
char command_separator = ';';
char escape_separator  = '\\';


boolean batteryCritical = false;
boolean batteryLow = false;
int  batteryLevel = -1;
int batteryCapacityRemaining = -1;
int batteryCapacityMax = -1;
int batteryVoltage = -1;
int batteryCurrent = -1;

// Attach a new CmdMessenger object to the default SerialUSB port
CmdMessenger cmdMessenger = CmdMessenger(Serial, field_separator, command_separator, escape_separator);

// This is the list of recognized BB commands. 
enum
{
  BBacknowledge,
  BBCommandList,
  BBsetled,
  BBsetheadlight,
  BBsetmode,
  BBClearScreen,
  BBUpdate,
  BBShowBattery,
  BBGetBatteryLevel,
  BBFillScreen,
  BBSetRow,
  BBPingRow,
  BBEchoRow,
  BBerror
};

// Callbacks define on which received commands we take action
void BBattachCommandCallbacks()
{
  // Attach callback methods
  cmdMessenger.attach(BBCommandList, ShowCommands);     // 1
  cmdMessenger.attach(BBCmdOne);                        // 2
  cmdMessenger.attach(BBsetled, Onsetled);              // 3
  cmdMessenger.attach(BBsetheadlight, Onsetheadlight);  // 4
  cmdMessenger.attach(BBClearScreen, OnClearScreen);    // 5
  cmdMessenger.attach(BBUpdate, OnUpdate);              // 6
  cmdMessenger.attach(BBShowBattery, OnShowBattery);    // 7
  cmdMessenger.attach(BBGetBatteryLevel, OnGetBatteryLevel);      // 8
  cmdMessenger.attach(BBFillScreen, OnFillScreen);      // 9
  cmdMessenger.attach(BBSetRow, OnSetRow);              // 10
  cmdMessenger.attach(BBPingRow, OnPingRow);            // 11
  cmdMessenger.attach(BBEchoRow, OnEchoRow);            // 12
}

// Show available commands
void ShowCommands() 
{
  Serial.println("\nAvailable commands");
  Serial.println(" 1;                       - This command list");
  Serial.println(" 2,<headlight state>;     - Set headlight.");
  Serial.println(" 3,<led state>;           - Set led. 0 = off, 1 = on"); 
  Serial.println(" 4;                       - Clear Screen"); 
  Serial.println(" 5;                       - Render and display screen"); 
  Serial.println(" 6;                       - Show Battery for 2 seconds"); 
  Serial.println(" 7;                      - Get battery level"); 
  Serial.println(" 8,r,g,b;                - Fill Screen"); 
  Serial.println(" 9,row,binary;           - Fill row of pixels"); 
  Serial.println(" 10,row,binary;           - ping round trip test of row"); 
  Serial.println(" 11,binary;               - Echo back row of pixels"); 
}

// Called when a received command has no attached function
void BBCmdOne()
{
  cmdMessenger.sendCmd(BBerror,"BB Error\n");
  Serial.print("BB Error ");

}

// Callback function that sets headlight on or off
void Onsetheadlight()
{


}

bool ledsOn = false;
// Callback function that sets deck lights on or off
void Onsetled()
{
  // Read led state argument, interpret string as boolean
  ledsOn = cmdMessenger.readBoolArg();
  cmdMessenger.sendCmd(BBacknowledge,ledsOn);
}

void OnClearScreen()
{
  clearScreen();
}

void OnUpdate() {

  if (batteryCritical) {
    fillScreen(rgbTo24BitColor(255,0,0));
  }
  // skip if charging
  if (0 && batteryCurrent < 32000) {
    return;
  }
  
  if (batteryLow) {
    clearScreen();
    //drawBattery();
  }
  leds.show();
  inited = true;
}

void OnFillScreen() {
  char color[4];
  int32_t *c = (int32_t *)&color[0];

  color[2] =  cmdMessenger.readInt32Arg();
  color[1] =  cmdMessenger.readInt32Arg();
  color[0] =  cmdMessenger.readInt32Arg();

  fillScreen(*c);
  
}

void OnShowBattery() {
  clearScreen();
  drawBattery();
  leds.show();
  delay(3000);
  batteryStats();
}

void OnGetBatteryLevel() {
  cmdMessenger.sendCmdStart(BBGetBatteryLevel);
  cmdMessenger.sendCmdArg(batteryLevel);
  cmdMessenger.sendCmdArg(batteryCapacityRemaining);
  cmdMessenger.sendCmdArg(batteryCapacityMax);
  cmdMessenger.sendCmdArg(batteryVoltage);
  cmdMessenger.sendCmdEnd();
}


// row is sized larger because some chars might be escaped
struct rowType { char row[2048]; };
typedef rowType rowType;

// Set a row of 10 r,g,b pixels from binary data
// The first an last pixel are for the side lights
void OnSetRow() {
  rowType response;
  char *pixels;
  uint32_t row;
  int i;
  int nPixels = 552 * 3; //sizeof(rowType);
  int r, g, b;
  int x, y;
  
  row = cmdMessenger.readInt32Arg();
  response =  cmdMessenger.readBinArg<rowType>();
  pixels = (char *)&response;
  cmdMessenger.unescape(pixels, nPixels);

#ifdef DEBUG_PIXELS
  Serial1.print("OnSetRow ");
  Serial1.print(row);
  Serial1.print(" = ");
  Serial1.println(nPixels);
  //Serial1.print("<");
#endif

  // Catch error case
  if (row > 7) {
    row = 7;
  }

  int startPixel = row * ledsPerStrip;
  
  for (i = 0; i < ledsPerStrip * 3; i += 3) {
    r = pixels[i];
    g = pixels[i+1];
    b = pixels[i+2];
    
#ifdef DEBUG_PIXELS2
    Serial1.print("setPixel(");
    Serial1.print(i);
    Serial1.print(",");
    Serial1.print(row);
    Serial1.print(",");
    Serial1.print(r);
    Serial1.print(",");
    Serial1.print(g);
    Serial1.print(",");
    Serial1.print(b);
#endif       
    leds.setPixel(startPixel + (i / 3), r, g, b);
#ifdef DEBUG_PIXELS2
    Serial1.println(")");
#endif
  }
}


// Echo a test row of 16 values
void OnEchoRow() {
  rowType response;
  char *pixels;
  uint32_t row;
  String echoReply;
  int b;
  
  response =  cmdMessenger.readBinArg<rowType>();
  pixels = (char *)&response;
  cmdMessenger.unescape(pixels, 16);
  for (int i = 0; i < 16; i++) {
    b = pixels[i];
    echoReply = echoReply + " " + b;
  }

  cmdMessenger.sendCmdStart(BBEchoRow);
  cmdMessenger.sendCmdArg(echoReply);
  cmdMessenger.sendCmdEnd();
  
}

// For benchmarks
void OnPingRow() {
  rowType response;
  int row;

  row = cmdMessenger.readInt32Arg();
  response =  cmdMessenger.readBinArg<rowType>();
}

// Create a 24 bit color value from R,G,B
uint32_t rgbTo24BitColor(byte r, byte g, byte b)
{
        uint32_t c;
        c = r;
        c <<= 8;
        c |= g;
        c <<= 8;
        c |= b;
        return c;
}

// Clear Screen
void clearScreen()
{
  int i;
  for (i = 0; i < NUM_LEDS; i++) {
    leds.setPixel(i, rgbTo24BitColor(0,0,0));
  }
  leds.show();
}

// Fill screen
void fillScreen(uint32_t color) {
  register int i;
  for (i=0; i < NUM_LEDS; i++) {
    leds.setPixel(i, color);
  }
  leds.show();
}

void batteryStats() {
  char batt[16];
  
  clearScreen();
  drawBattery();
  leds.show();
  delay(5000);

  clearScreen();
  sprintf(batt, "%d v", batteryVoltage);
  //leds.print(batt, 12, 1, 1);
  //leds.show();
  delay(5000);

  clearScreen();
  sprintf(batt, "%d C", batteryCapacityRemaining);
  //leds.print(batt, 12, 1, 1);
  //leds.show();
  delay(5000);

  clearScreen();
  sprintf(batt, "%d F", batteryCapacityMax);
  //leds.print(batt, 12, 1, 1);
  //leds.show();
  delay(5000);

  clearScreen();
  sprintf(batt, "%d A", batteryCurrent);
  //leds.print(batt, 12, 1, 1);
  leds.show();
  delay(5000);
  
}

#include <Wire.h>  // Wire library for communicating over I2C
#define BQ34Z100 0x55

unsigned int nonBlockingRead() {
  for (int i = 0; (i < 3) &&  (!Wire.available()); i++) {
    delay(1);
  }
  if (Wire.available()) {
    return(Wire.read());
  } else {
    return (-1);
  }
}

int getBattery8(int reg) {
  Wire.beginTransmission(BQ34Z100);
  Wire.write(reg);
  Wire.endTransmission();
  Wire.requestFrom(BQ34Z100,1);
  return (nonBlockingRead());
}

int getBattery16(int register1, int register2) {
  unsigned int low;
  unsigned int high;
  unsigned int high1;
  int value = -1;
  
  if ((value = getBattery8(register1)) != -1) {
    low = value;
    if ((value = getBattery8(register2)) != -1) {
      high1 = value << 8;
      high1 = high1 + low;
      return(high1);
    }
  }
  return(-1);
}

int getBatteryAll() {

  int value;

  // Capacity in %age
  value = getBattery8(0x02);
  if (value > 100) {
    value = 100;
  }
  if (value >= 0) {
    batteryLevel = value;
  }

  // Capacity in mAH
  value = getBattery16(0x04, 0x05);
  if (value != -1) {
    batteryCapacityRemaining = value;
  }
  
  // Learned Capacity 
  value = getBattery16(0x06, 0x07);
  if (value != -1) {
    batteryCapacityMax = value;
  }

  // Voltage
  value = getBattery16(0x08, 0x09);
  if (value != -1) {
    batteryVoltage = value;
  }

  // Current
  value = getBattery16(0xa, 0xb);
  if (value != -1) {
    batteryCurrent = value;
  }
  
  return(batteryLevel);
}

int getBatteryLevel() {

  return 50;
  int value;

  // Capacity in %age
  value = getBattery8(0x02);
  if (value > 100) {
    value = 100;
  }
  if (value >= 0) {
    batteryLevel = value;
  }
  
  return(batteryLevel);
}


void drawBattery() {
  uint32_t color;
  uint16_t x;
  int level;
  int battWidth = 20;
  int battLeft = 23 - battWidth / 2;
  int startRow = 34;
  int row;
  int endRow = 84;
  
  level = getBatteryLevel() / 2; // convert 100 to max of 50

  row = startRow;
  
  // White Battery Shell with Green level

  // Battery Bottom
  for (x = 0; x < battWidth; x++) {
    translatePixel(x + battLeft, row, rgbTo24BitColor(RGB_MAX, RGB_MAX, RGB_MAX));
  }
  row++;

  // Battery Sides
  for (; row <= endRow; row++) {
    translatePixel(battLeft, row, rgbTo24BitColor(RGB_MAX, RGB_MAX, RGB_MAX));
    translatePixel(battLeft + battWidth - 1, row, rgbTo24BitColor(RGB_MAX, RGB_MAX, RGB_MAX));
  }

  // Battery Top
  for (x = 0; x < battWidth; x++) {
    translatePixel(x + battLeft, row, rgbTo24BitColor(RGB_MAX, RGB_MAX, RGB_MAX));
  }
  row++;

  // Battery button
  for (x = battWidth / 2 - 6; x < battWidth / 2 + 6; x++) {
    translatePixel(x + battLeft, row, rgbTo24BitColor(RGB_MAX, RGB_MAX, RGB_MAX));
    translatePixel(x + battLeft, row+1, rgbTo24BitColor(RGB_MAX, RGB_MAX, RGB_MAX));
    translatePixel(x + battLeft, row+2, rgbTo24BitColor(RGB_MAX, RGB_MAX, RGB_MAX));
    translatePixel(x + battLeft, row+3, rgbTo24BitColor(RGB_MAX, RGB_MAX, RGB_MAX));
  }

  // Get level failed
  if (level < 0) {
    // RED
    for (row = startRow + 1; row < endRow ; row++) {
      for (x = battLeft; x < battLeft + battWidth; x++) {
        translatePixel(x, row, rgbTo24BitColor(255, 0, 0));
      }
    }
  } else {
    // Battery Level
    uint32_t batteryColor;
    if (batteryCritical) {
      batteryColor = rgbTo24BitColor(255, 0, 0);
    } else if (batteryLow) {
      batteryColor = rgbTo24BitColor(255, 40, 0);
    }  else {
      batteryColor = rgbTo24BitColor(0, 255, 0);
    }
    for (row = startRow + 1; row < startRow + level ; row++) {
      for (x = battLeft; x < battLeft + battWidth; x++) {
        translatePixel(x, row, batteryColor);
      }
    }
  }
}


void setup() {
  char batt[16];
  
  // Console for debugging
  Serial.begin(115200);
  Serial1.begin(115200); // Initialize Serial Monitor USB
  Serial.println("Goodnight moon!");
  Serial1.println("Goodnight moon!");

  pinMode(AUX_PIN, OUTPUT);
  pinMode(SPEED_PIN, OUTPUT);

  limitBoardSpeed(false);
  
  BBattachCommandCallbacks();
  cmdMessenger.sendCmd(BBacknowledge,"BB Ready\n");
  ShowCommands();
  
  leds.begin();
  //fillScreen(rgbTo24BitColor(13,30,96));
  //leds.show();

  for (int i = 0; i < 100; i++) {
    leds.setPixel((0 * 552) + i,0,0,40);
    leds.show();
  }

}

uint8_t c = 0;

#define RED    0x160000
#define GREEN  0x001600
#define BLUE   0x000016
#define YELLOW 0x101400
#define PINK   0x120009
#define ORANGE 0x100400
#define WHITE  0x202020


void loop() {
  if (inited == false) {
    //fillScreen(rgbTo24BitColor(c,c + 10,48));
    //drawBattery();
    clearScreen();
    testPattern();
    leds.show();
    delay(10000);
    clearScreen();
    fillScreen(RED);
    leds.show();
    delay(10000);
    clearScreen();
    fillScreen(GREEN);
    leds.show();
    delay(10000);
    clearScreen();
    fillScreen(BLUE);
    leds.show();
    delay(10000);
    clearScreen();
    fillScreen(WHITE);
    leds.show();
    delay(10000);
    c++;
    if (c > 64)
      c = 0;
  }
  cmdMessenger.feedinSerialData();
}


// Enable the speed pin -- true means make the board speed limited
void limitBoardSpeed(bool limit) {
    if (limit) {
      digitalWrite(SPEED_PIN, LOW);
    } else {
      digitalWrite(SPEED_PIN, HIGH);
    }
}


struct TranslationMap {
                int y;
                int startX;
                int endX;
                int stripDirection;
                int stripNumber;
                int stripOffset;
                };


struct TranslationMap boardMap[] = {
  0,23,22,-1,8,452,
  1,20,25,1,8,446,
  2,27,18,-1,8,436,
  3,16,29,1,8,422,
  4,30,15,-1,8,406,
  5,14,31,1,8,388,
  6,33,12,-1,8,366,
  7,11,34,1,8,342,
  8,31,14,-1,8,324,
  9,14,31,1,8,306,
  10,32,13,-1,8,286,
  11,12,33,1,8,264,
  12,34,11,-1,8,240,
  13,11,34,1,8,216,
  14,35,10,-1,8,190,
  15,9,36,1,8,162,
  16,37,8,-1,8,132,
  17,8,37,1,8,102,
  18,38,7,-1,8,70,
  19,6,39,1,8,36,
  20,40,5,-1,8,0,
  21,40,5,-1,7,0,
  22,7,38,1,7,36,
  23,38,7,-1,7,68,
  24,6,39,1,7,100,
  25,39,6,-1,7,134,
  26,5,40,1,7,168,
  27,40,5,-1,7,204,
  28,4,41,1,7,240,
  29,41,4,-1,7,278,
  30,4,41,1,7,316,
  31,42,3,-1,7,354,
  32,3,42,1,7,394,
  33,42,3,-1,7,434,
  34,2,43,1,7,474,
  35,43,2,-1,6,0,
  36,2,43,1,6,42,
  37,44,1,-1,6,84,
  38,1,44,1,6,128,
  39,44,1,-1,6,172,
  40,1,44,1,6,216,
  41,44,1,-1,6,260,
  42,1,44,1,6,304,
  43,44,1,-1,6,348,
  44,0,45,1,6,392,
  45,45,0,-1,6,438,
  46,0,45,1,6,484,
  47,45,0,-1,5,0,
  48,0,45,1,5,46,
  49,45,0,-1,5,92,
  50,0,45,1,5,138,
  51,45,0,-1,5,184,
  52,0,45,1,5,230,
  53,45,0,-1,5,276,
  54,0,45,1,5,322,
  55,45,0,-1,5,368,
  56,0,45,1,5,414,
  57,45,0,-1,5,460,
  58,0,45,1,5,506,
  59,45,0,-1,4,0,
  60,0,45,1,4,46,
  61,45,0,-1,4,92,
  62,0,45,1,4,138,
  63,45,0,-1,4,184,
  64,0,45,1,4,230,
  65,45,0,-1,4,276,
  66,0,45,1,4,322,
  67,45,0,-1,4,368,
  68,0,45,1,4,414,
  69,45,0,-1,4,460,
  70,1,44,1,4,506,
  71,44,1,-1,3,0,
  72,1,44,1,3,44,
  73,44,1,-1,3,88,
  74,1,44,1,3,132,
  75,44,1,-1,3,176,
  76,1,44,1,3,220,
  77,44,1,-1,3,264,
  78,1,44,1,3,308,
  79,43,2,-1,3,352,
  80,2,43,1,3,394,
  81,43,2,-1,3,436,
  82,2,43,1,3,478,
  83,42,3,-1,2,0,
  84,3,42,1,2,40,
  85,42,3,-1,2,80,
  86,3,42,1,2,120,
  87,42,3,-1,2,160,
  88,4,41,1,2,200,
  89,41,4,-1,2,238,
  90,4,41,1,2,276,
  91,40,5,-1,2,314,
  92,5,40,1,2,350,
  93,40,5,-1,2,386,
  94,6,39,1,2,422,
  95,39,6,-1,2,456,
  96,6,39,1,2,490,
  97,39,6,-1,1,0,
  98,7,38,1,1,34,
  99,38,7,-1,1,66,
  100,8,37,1,1,98,
  101,37,8,-1,1,128,
  102,9,36,1,1,158,
  103,36,9,-1,1,186,
  104,10,35,1,1,214,
  105,38,7,-1,1,240,
  106,8,37,1,1,272,
  107,36,9,-1,1,302,
  108,9,36,1,1,330,
  109,35,10,-1,1,358,
  110,10,35,1,1,384,
  111,34,11,-1,1,410,
  112,12,33,1,1,434,
  113,32,13,-1,1,456,
  114,14,31,1,1,476,
  115,34,11,-1,1,494,
  116,14,31,1,1,518,
  117,26,19,-1,1,536
};

struct TranslationMap boardMapAzul[] = {
  0,23,22,-1,8,452,
  1,20,25,1,8,446,
  2,27,18,-1,8,436,
  3,16,29,1,8,422,
  4,30,15,-1,8,406,
  5,14,31,1,8,388,
  6,33,12,-1,8,366,
  7,11,34,1,8,342,
  8,31,14,-1,8,324,
  9,14,31,1,8,306,
  10,32,13,-1,8,286,
  11,12,33,1,8,264,
  12,34,11,-1,8,240,
  13,11,34,1,8,216,
  14,35,10,-1,8,190,
  15,9,36,1,8,162,
  16,37,8,-1,8,132,
  17,8,37,1,8,102,
  18,38,7,-1,8,70,
  19,6,39,1,8,36,
  20,40,5,-1,8,0,
  21,40,5,-1,7,0,
  22,7,38,1,7,36,
  23,38,7,-1,7,68,
  24,6,39,1,7,100,
  25,39,6,-1,7,134,
  26,5,40,1,7,168,
  27,40,5,-1,7,204,
  28,4,40,1,7,240,
  29,41,4,-1,7,277,
  30,4,41,1,7,315,
  31,42,3,-1,7,353,
  32,3,42,1,7,393,
  33,42,3,-1,7,433,
  34,2,43,1,7,473,
  35,43,2,-1,6,0,
  36,2,43,1,6,42,
  37,44,1,-1,6,84,
  38,1,44,1,6,128,
  39,44,1,-1,6,172,
  40,1,44,1,6,216,
  41,44,1,-1,6,260,
  42,1,44,1,6,304,
  43,44,1,-1,6,348,
  44,0,45,1,6,392,
  45,45,0,-1,6,438,
  46,0,45,1,6,484,
  47,45,0,-1,5,0,
  48,0,45,1,5,46,
  49,45,0,-1,5,92,
  50,0,45,1,5,138,
  51,45,0,-1,5,184,
  52,0,45,1,5,230,
  53,45,0,-1,5,276,
  54,0,45,1,5,322,
  55,45,0,-1,5,368,
  56,0,45,1,5,414,
  57,45,0,-1,5,460,
  58,0,45,1,5,506,
  59,45,0,-1,4,0,
  60,0,45,1,4,46,
  61,45,0,-1,4,92,
  62,0,45,1,4,138,
  63,45,0,-1,4,184,
  64,0,45,1,4,230,
  65,45,0,-1,4,276,
  66,0,45,1,4,322,
  67,45,0,-1,4,368,
  68,0,45,1,4,414,
  69,45,0,-1,4,460,
  70,1,44,1,4,506,
  71,44,1,-1,3,0,
  72,1,44,1,3,44,
  73,44,1,-1,3,88,
  74,1,44,1,3,132,
  75,44,1,-1,3,176,
  76,1,44,1,3,220,
  77,44,1,-1,3,264,
  78,1,44,1,3,308,
  79,43,2,-1,3,352,
  80,2,43,1,3,394,
  81,43,2,-1,3,436,
  82,2,43,1,3,478,
  83,42,3,-1,2,0,
  84,3,42,1,2,40,
  85,42,3,-1,2,80,
  86,3,42,1,2,120,
  87,42,3,-1,2,160,
  88,4,41,1,2,200,
  89,41,4,-1,2,238,
  90,4,41,1,2,276,
  91,40,5,-1,2,314,
  92,5,40,1,2,350,
  93,40,5,-1,2,386,
  94,6,39,1,2,422,
  95,39,6,-1,2,456,
  96,6,39,1,2,490,
  97,39,6,-1,1,0,
  98,7,38,1,1,34,
  99,38,7,-1,1,66,
  100,8,37,1,1,98,
  101,37,8,-1,1,128,
  102,9,36,1,1,158,
  103,36,9,-1,1,186,
  104,10,35,1,1,214,
  105,38,7,-1,1,240,
  106,8,37,1,1,272,
  107,36,9,-1,1,302,
  108,9,36,1,1,330,
  109,35,10,-1,1,358,
  110,10,35,1,1,384,
  111,34,11,-1,1,410,
  112,12,33,1,1,434,
  113,32,13,-1,1,456,
  114,14,31,1,1,476,
  115,34,11,-1,1,494,
  116,14,31,1,1,518,
  117,26,19,-1,1,536};


#define PIXEL_RED  0;
#define  PIXEL_GREEN 1;
#define  PIXEL_BLUE 2;

void translatePixel(int x, int y, int color) {
  int startX = boardMap[y].startX;
  int endX = boardMap[y].endX;
  int stripOffset = boardMap[y].stripOffset;
  int stripNumber = boardMap[y].stripNumber - 1;
  int stripDirection = boardMap[y].stripDirection;

  int pixelOffset;

  // Check it off screen
  if (x < min (startX, endX)  && x > max(startX, endX))
    return;

  // Calculate strip number and offset first
  if (stripDirection == 1) {
    pixelOffset = stripOffset + x - startX ;
    leds.setPixel(stripNumber * ledsPerStrip + pixelOffset, color);
  } else {
    pixelOffset = stripOffset + startX - x;
    leds.setPixel(stripNumber * ledsPerStrip + pixelOffset, color);
  }
} 

void testPattern() {
  int row;
  int endRow = 118;
  for (row = 0; row < endRow; row++) {
    translatePixel(22, row, rgbTo24BitColor(RGB_MAX, RGB_MAX, RGB_MAX));
    translatePixel(23, row, rgbTo24BitColor(RGB_MAX, RGB_MAX, RGB_MAX));
  }
}
