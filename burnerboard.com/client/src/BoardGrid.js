import React, { Component } from 'react';
import ReactDataGrid from 'react-data-grid';
import 'bootstrap/dist/css/bootstrap.css';
import PropTypes from 'prop-types';

// Custom Formatter component
class PercentCompleteFormatter extends React.Component {
    // static propTypes = {
    //     value: PropTypes.number.isRequired
    // };

    render() {
        const percentComplete = this.props.value + '%';
        return (
            <div className="progress" style={{ marginTop: '20px' }}>
                <div className="progress-bar" role="progressbar" aria-valuenow="60" aria-valuemin="0" aria-valuemax="100" style={{ width: percentComplete }}>
                    {percentComplete}
                </div>
            </div>);
    }
}

const boardsJSON = {
    boards: [
        {
            board_name: "loading boards...",
            last_seen: "",
            is_online: "false",
            battery_level: 0
        }
    ]
};

const API = '/boards';

class BoardGrid extends React.Component {
    constructor(props, context) {
        super(props, context);

        this._columns = [
            {
                key: 'board_name',
                name: 'Board Name'
            },
            {
                key: 'last_seen',
                name: 'Last Seen'
            },
            {
                key: 'is_online',
                name: 'Is Online'
            },
            {
                key: 'battery_level',
                name: 'Battery Level',
                formatter: PercentCompleteFormatter
            }
        ];

        this.state = {
            boardData: boardsJSON.boards,
        };

    }

    componentDidMount() {

        fetch(API)
            .then(response => response.json())
            .then(data => this.setState({
                boardData: data.map(item => ({
                    board_name: `${item.board_name}`,
                    last_seen: `${item.last_seen}`,
                    is_online: `${item.is_online}`,
                    battery_level: `${item.battery_level}`,
                }))
            }))
            .catch(error => this.setState({ error }));

    }

    getRandomDate = (start, end) => {
        return new Date(start.getTime() + Math.random() * (end.getTime() - start.getTime())).toLocaleDateString();
    };


    rowGetter = (i) => {
        return this.state.boardData[i];
    };

    render() {
        console.log("Rendering BoardGrid");
        return (
            <ReactDataGrid
                columns={this._columns}
                rowGetter={this.rowGetter}
                rowsCount={this.state.boardData.length}
                minHeight={500} />);
    }
}

export default BoardGrid;

