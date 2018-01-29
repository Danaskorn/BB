import React, { Component } from 'react';
import AudioList from './AudioList';
import VideoList from './VideoList';
import GlobalMenu from './GlobalMenu';
import BoardGrid from './BoardGrid';
import BatteryHistoryGrid from './BatteryHistoryGrid';
import GoogleDriveMediaPicker from './GoogleDriveMediaPicker';
import ManageMediaGrid from './ManageMediaGrid';
import ProfileGrid from './ProfileGrid';
import AddProfile from './AddProfile';

class BBApp extends Component {

  constructor(props) {
    super(props);

    this.state = {
      currentAppBody: "none",
      currentBoard: "Select Board",
      currentProfile: "Select Profile",
      currentProfileIsGlobal: false,
      activeProfileIsGlobal: false,
      activeProfile: "",
    };

    this.handleSelect = this.handleSelect.bind(this);
  }

  handleSelect(info) {

    var API;

    console.log(`selected ${info.key}`);

    if (info.key.startsWith("AppBody-")) {
      this.setState({ currentAppBody: info.key });
    }
    else if (info.key === "ActivateProfile") {

      API = '/boards/' + this.state.currentBoard + '/activeProfile/' + this.state.currentProfile + "/isGlobal/" + this.state.currentProfileIsGlobal;
      console.log(API)
      fetch(API, {
        method: 'POST',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          'Authorization': window.sessionStorage.JWT,
        },
      }).then((res) => res.json())
        .then((data) => {
          console.log(data)
          this.setState({
            activeProfile: this.state.currentProfile,
            activeProfileIsGlobal: this.state.currentProfileIsGlobal,
          });

        })
        .catch((err) => console.log(err));

    }
    else if (info.key.startsWith("board-")) {

      var selectedBoard = info.key.slice(6);

      API = '/boards/' + selectedBoard;
      console.log(API)
      fetch(API, {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          'Authorization': window.sessionStorage.JWT,
        },
      }).then((res) => res.json())
        .then((data) => {
          console.log(data)
          var activeProfile = data[0].profile;
          var activeProfileIsGlobal = data[0].isGlobal;
          console.log("active profile " + activeProfile);
          this.setState({
            activeProfile: activeProfile,
            activeProfileIsGlobal: activeProfileIsGlobal,
            currentBoard: selectedBoard
          });
        })
        .catch((err) => console.log(err));

    }
    else if (info.key.startsWith("profile-")) {
      this.setState({
        currentProfile: info.key.slice(8),
        currentProfileIsGlobal: false
      });
    }
    else if (info.key.startsWith("globalProfile-")) {
      this.setState({
        currentProfile: info.key.slice(14),
        currentProfileIsGlobal: true
      });
    }
    ;
  }


  render() {

    let appBody = null;
    console.log(this.state.currentAppBody);

    switch (this.state.currentAppBody) {
      case "AppBody-CurrentStatuses":
        appBody = <BoardGrid />;
        break;
      case "AppBody-BatteryHistory":
        appBody = <BatteryHistoryGrid currentBoard={this.state.currentBoard} />;
        break;
      case "AppBody-ReorderAudio":
        if (this.state.currentProfileIsGlobal)
          appBody = <AudioList currentProfile={this.state.currentProfile} />;
        else
          appBody = <AudioList currentBoard={this.state.currentBoard} currentProfile={this.state.currentProfile} />;
        break;
      case "AppBody-ReorderVideo":
        if (this.state.currentProfileIsGlobal)
          appBody = <VideoList currentProfile={this.state.currentProfile} />;
        else
          appBody = <VideoList currentBoard={this.state.currentBoard} currentProfile={this.state.currentProfile} />;
        break;
      case "AppBody-LoadFromGDrive":
        if (this.state.currentProfileIsGlobal)
          appBody = <GoogleDriveMediaPicker currentProfile={this.state.currentProfile} />;
        else
          appBody = <GoogleDriveMediaPicker currentBoard={this.state.currentBoard} currentProfile={this.state.currentProfile} />;
        break;
      case "AppBody-ManageMedia":
        if (this.state.currentProfileIsGlobal)
          appBody = <ManageMediaGrid currentProfile={this.state.currentProfile} />;
        else
          appBody = <ManageMediaGrid currentBoard={this.state.currentBoard} currentProfile={this.state.currentProfile} />;
        break;
      case "AppBody-ManageProfiles":
        appBody = <ProfileGrid />;
        break;
      case "AppBody-AddProfile":
        appBody = <AddProfile />;
        break;
      default:
        if (this.state.currentBoard !== "Select Board") {
          appBody = <div style={{
            'backgroundColor': 'lightblue',
            'margin': '1cm 1cm 1cm 1cm',
            'padding': '10px 5px 15px 20px'
          }}><p>You selected {this.state.currentBoard}.</p><p>Board-specific options available.</p><p>Select a profile to manage media. The * indicates the active profile on {this.state.currentBoard}.</p></div>;
        }
        else {
          appBody = <div style={{
            'backgroundColor': 'lightblue',
            'margin': '1cm 1cm 1cm 1cm',
            'padding': '10px 5px 15px 20px'
          }}><p>Global options available.</p> <p>Please select a board for board-specific options.</p></div>;
        }
        break;
    };

    console.log("rendering in app " + appBody);


    return (
      <div className="BBApp" style={{ margin: 0 }}>
        <GlobalMenu handleSelect={this.handleSelect} currentBoard={this.state.currentBoard} activeProfile={this.state.activeProfile} activeProfileIsGlobal={this.state.activeProfileIsGlobal} currentProfile={this.state.currentProfile} />
        {appBody}
      </div>
    );
  }
}

export default BBApp;

