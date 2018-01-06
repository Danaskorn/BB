import React, { Component } from 'react';
import Menu, { SubMenu, Item as MenuItem } from 'rc-menu';
import 'rc-menu/assets/index.css';

const boardsJSON = {
    boards: [
        {
            board_name: "loading boards...",
            last_seen: "12/18/17 05:57:01",
            is_online: false,
            battery_level: 0
        }
    ]
};

const getBoardNames = function () {
    return boardsJSON.boards.map(item => ({
        board_name: `${item.board_name}`,
    }))
};

const API = '/boards';

class GlobalMenu extends Component {

    constructor(props) {

        console.log("  in constructor " + props.currentBoard);
   
        super(props);
        this.state = {
            boardNames: getBoardNames(),
            currentBoard: props.currentBoard,
        };

        this.onOpenChange = this.onOpenChange.bind(this);
        this.handleSelect = this.props.handleSelect.bind(this);
    }

    componentWillReceiveProps(nextProps){
        this.setState({ currentBoard: nextProps.currentBoard });
    };

    componentDidMount() {

        fetch(API)
          .then(response => response.json())
          .then(data => this.setState({
            boardNames: data.map(item => ({
                board_name: `${item.name}`,
            }))
          }))
          .catch(error => this.setState({ error}));
    
      }

    onOpenChange(value) {
        console.log('onOpenChange', value);
    };

    render() {
        console.log("rendering in menu " + this.state.currentBoard);
 
        var optionsDisabled=false;
        if(this.state.currentBoard=="Select Board"){
            optionsDisabled=true;
        };

        return (
            <div style={{ margin: 0 }}>
                <Menu mode="horizontal" openAnimation="slide-up" triggerSubMenuAction="hover" onSelect={this.handleSelect} onOpenChange={this.onOpenChange}>
                    <SubMenu title={this.state.currentBoard} key="1">
                        {this.state.boardNames.map(item => (
                            <MenuItem key={item.board_name}>{item.board_name}
                            </MenuItem>))
                        }
                    </SubMenu>
                    <SubMenu disabled={optionsDisabled} title={<span>Options</span>} key="2">
                        <MenuItem key="AppBody-BatteryHistory">Battery History</MenuItem>
                        <MenuItem key="AppBody-ReorderAudio">Reorder Audio</MenuItem>
                        <MenuItem key="AppBody-ReorderVideo">Reorder Video</MenuItem>
                        <MenuItem disabled={true} key="AppBody-UploadFromDesktop">Upload From Desktop</MenuItem>
                        <MenuItem key="AppBody-LoadFromGDrive">Load From G Drive</MenuItem>
                        <MenuItem disabled={true} key="AppBody-LoadFromDropBox">Load From DropBox</MenuItem>
                    
                    </SubMenu>
                    <SubMenu title={<span>Global</span>} key="3">
                        <MenuItem key="AppBody-CurrentStatuses">Current Statuses</MenuItem>
                        <MenuItem disabled={true} key="AppBody-MapEm">Map 'Em</MenuItem>
                        
                    </SubMenu>
                </Menu>
            </div>
        );
    };
};

export default GlobalMenu;

