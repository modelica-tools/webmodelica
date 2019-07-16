import React, { Component } from 'react';
import { WmContainer } from '../partials/container'
import { Button, ButtonGroup, Form, Alert, Row, Col } from 'react-bootstrap'
//@ts-ignore
import Octicon from 'react-octicon'
import { ApiClient, baseApiPrefix } from '../services/api-client'
import { Redirect } from 'react-router'
import { defaultMapDispatchToProps, mapAuthenticationToProps } from '../redux'
import { Action, updateToken, notifyError } from '../redux/index'
import { renderErrors } from '../partials/errors'
import { connect } from 'react-redux'
import { bindActionCreators } from 'redux'
import * as R from 'ramda'
import { userIsAuthenticated, UserAuth, AuthProvider, ApiError } from '../models/index';
//@ts-ignore
import {cookies, local, db, session, subscribe} from 'brownies';

interface Props {
  api: ApiClient
  history: any
  notifyError(msg:string): void
  authentication?: UserAuth
  updateToken(token:string): void
}

interface State {
  providers: AuthProvider[]
  errors: string[]
  developerUsers: string[]
}

const isDeveloperProvider =(p:AuthProvider) => p.provider === 'developer'

class LoginComponentCon extends React.Component<Props, State> {
  private username: string = ''
  private password: string = ''

  constructor(p:Props) {
    super(p)
    this.state = { errors: [], providers: [], developerUsers: [] }
  }

  private handleSubmit(ev: any) {
    const props = this.props
    const username = this.username
    const pw = this.password
    props.api.login(username, pw)
      .then(() => props.history.push('/projects'))
      .catch(err => {
        this.setState({ errors: [err] })
      })
    ev.preventDefault()
  }

  componentDidMount() {
    //check if auth-service redireted us here and gave us a cookie
    if(cookies.token) {
      this.props.updateToken(cookies.token)
    } else if(!userIsAuthenticated(this.props.authentication)) {
      this.props.api.getAuthenticationProviders()
        .then(providers => {
          if (providers.find(isDeveloperProvider)) {
            return this.props.api.getDeveloperUsers()
              .then(users => [providers, users])
          } else { return Promise.resolve([providers, [] as string[]])}
        })
        .then(([providers, developerUsers]) => this.setState({providers: providers as AuthProvider[], developerUsers: developerUsers as string[]}))
        .catch((err:ApiError) => this.props.notifyError("couldn't fetch OAuth providers: "+err.statusText))
    }
  }

  developerLogin() {
    const users = this.state.developerUsers
    if (!R.isEmpty(users)) {
      return (<>
        <Form action={`api/v1/auths/developer/callback`} style={{marginTop: '4em'}}>
          <Form.Row>
          <Form.Group as={Col}>
              <Form.Label>Developer Login</Form.Label>
              <Form.Control as="select" id="username" name="username">
              {users.map(username => <option key={username}>{username}</option>)}
            </Form.Control>
          </Form.Group>
          </Form.Row>
          <Button type="submit" variant="secondary">Developer Login</Button>
        </Form>
      </>)
    } else {
      return (<></>)
    }
  }

  render() {
    const usernameChanged = (ev: any) => this.username = ev.target.value
    const passwordChanged = (ev: any) => this.password = ev.target.value
    if (userIsAuthenticated(this.props.authentication)) {
      return (<Redirect to="/projects" />)
    } else {
      return (
        <div className="card sm-10 mx-auto">
          <h5 className="card-title">Login</h5>
          <div className="card-body">
            <Form onSubmit={this.handleSubmit.bind(this)}>
              <Form.Group controlId="formUsername">
                <Form.Label>Username</Form.Label>
                <Form.Control required placeholder="Enter username" onChange={usernameChanged} disabled/>
              </Form.Group>
              <Form.Group controlId="formPassword">
                <Form.Label>Password</Form.Label>
                <Form.Control required type="password" placeholder="Password" onChange={passwordChanged} disabled/>
              </Form.Group>
              {renderErrors(this.state.errors)}
              <ButtonGroup>
                {this.state.providers.filter(p => !isDeveloperProvider(p)).map(p =>
                  (<Button variant="secondary" href={p.uri} key={p.name} style={ {backgroundColor: p.color, borderColor: p.color} }>
                    {p.icon && (<Octicon name={p.icon}/>)}&nbsp;
                    {p.name}
                  </Button>)
                )}
                <Button variant="primary" type="submit" disabled>
                  <Octicon name="sign-in" /> Submit
                </Button>
              </ButtonGroup>
            </Form>
            {this.developerLogin()}
          </div>
          </div>
      )
    }
  }
}

function dispatchToProps(dispatch: (a: Action) => any) {
  return bindActionCreators({ updateToken, notifyError }, dispatch)
}

const LoginComponent = connect(mapAuthenticationToProps, dispatchToProps)(LoginComponentCon)
export default LoginComponent
