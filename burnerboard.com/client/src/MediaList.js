import React, { Component } from 'react';
//import ReactDOM from 'react-dom';
import { DragDropContext, Droppable, Draggable } from 'react-beautiful-dnd';

const getItems = count =>  
  getDirectoryJSON.audio.map(item => ({
    id: `${item.localName}`,
    content: `${item.localName}`,
  }));
 
// a little function to help us with reordering the result
const reorder = (list, startIndex, endIndex) => {
  const result = Array.from(list);
  const [removed] = result.splice(startIndex, 1);
  result.splice(endIndex, 0, removed);

  return result;
};

// using some little inline style helpers to make the app look okay
const grid = 8;
const getItemStyle = (draggableStyle, isDragging) => ({
  // some basic styles to make the items look a bit nicer
  userSelect: 'none',
  padding: grid * 2,
  margin: `0 0 ${grid}px 0`,

  // change background colour if dragging
  background: isDragging ? 'lightgreen' : 'grey',

  // styles we need to apply on draggables
  ...draggableStyle,
});
const getListStyle = isDraggingOver => ({
  background: isDraggingOver ? 'lightblue' : 'lightgrey',
  padding: grid,
  width: 250,
});

const getDirectoryJSON = {
  "audio": [
    {
      "URL": "None",
      "localName": "Loading Song 1...",
      "Size": 0,
      "Length": 0
    },
    {
      "URL": "None",
      "localName": "Loading Song 2...",
      "Size": 0,
      "Length": 0
    },
    {
      "URL": "None",
      "localName": "Loading Song 3...",
      "Size": 0,
      "Length": 0
    }
  ],
  "video": [
    {
      "URL": "https://storage.googleapis.com/burner-board/BurnerBoardMedia/vega/tunnels.mp4",
      "localName": "tunnels.mp4",
      "SpeachCue": "Tunnels"
    },
    {
      "URL": "https://storage.googleapis.com/burner-board/BurnerBoardMedia/vega/BouncingComets.mp4",
      "localName": "BouncingComets.mp4"
    },
    {
      "URL": "https://storage.googleapis.com/burner-board/BurnerBoardMedia/vega/RedDevil.mp4",
      "localName": "RedDevil.mp4"
    },
    {
      "Algorithm": "modeFire(kModeFireDistrikt)"
    }
  ]
};

class MediaList extends Component {
  constructor(props) {
    super(props);
    this.state = {
      items: getItems(10),
    };
    this.onDragEnd = this.onDragEnd.bind(this);

//    alert(state.items.item[0].toString());

  }

  onDragEnd(result) {
    // dropped outside the list
    if (!result.destination) {
      return;
    }

    const items = reorder(
      this.state.items,
      result.source.index,
      result.destination.index
    );

    this.setState({
      items,
    });
  }

  // Normally you would want to split things out into separate components.
  // But in this example everything is just done in one place for simplicity
  render() {
    return (
      <DragDropContext onDragEnd={this.onDragEnd}>
        <Droppable droppableId="droppable">
          {(provided, snapshot) => (
            <div
              ref={provided.innerRef}
              style={getListStyle(snapshot.isDraggingOver)}
            >
              {this.state.items.map(item => (
                <Draggable key={item.id} draggableId={item.id}>
                  {(provided, snapshot) => (
                    <div>
                      <div
                        ref={provided.innerRef}
                        style={getItemStyle(
                          provided.draggableStyle,
                          snapshot.isDragging
                        )}
                        {...provided.dragHandleProps}
                      >
                        {item.content}
                      </div>
                      {provided.placeholder}
                    </div>
                  )}
                </Draggable>
              ))}
              {provided.placeholder}
            </div>
          )}
        </Droppable>
      </DragDropContext>
    );
  }
}

export default MediaList;
