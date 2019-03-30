
import { File, Project, TokenWrapper, Session, AppState, UserAuth, CompilerError, SimulationResult, TableFormat, SimulateRequest } from '../models/index'
import React, { Component } from 'react';
import { Store } from 'redux';
import { updateToken } from '../redux/index';

function rejectError(res: Response): Promise<Response> {
  if (res.ok) return Promise.resolve(res)
  else {
    console.error("api error:", res)
    return res.text().then(txt => Promise.reject(txt || res.statusText))
  }
}

const authHeader = "Authorization"
const apiPrefix = "/api/v1/"

export class ApiClient {
  private readonly base: string
  private readonly store: Store<AppState>

  constructor(store: Store<AppState>) {
    this.store = store
    this.base = window.location.protocol + "//" + window.location.host + apiPrefix
  }

  private userUri(): string {
    return this.base + "users"
  }
  private projectUri(): string {
    return this.base + "projects"
  }
  private sessionUri(): string {
    return this.base + "sessions"
  }

  private updateWSToken(res: Response): Response {
    const headerOpt = res.headers.get(authHeader)
    if (headerOpt) {
      this.store.dispatch(updateToken(headerOpt))
    }
    return res
  }
  private token(): string { return this.store.getState().authentication!.token.raw }

  public login(user: string, pw: string): Promise<TokenWrapper> {
    return fetch(this.userUri() + "/login", {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        "Accept": 'application/json'
      },
      body: JSON.stringify({ username: user, password: pw })
    })
      .then(rejectError)
      .then(res => res.json())
      .then((t: TokenWrapper) => {
        this.store.dispatch(updateToken(t.token))
        return t
      })
  }

  public projects(): Promise<Project[]> {
    return fetch(this.projectUri(), {
      method: 'GET',
      headers: {
        [authHeader]: this.token(),
        'Accept': 'application/json'
      }
    })
      .then(rejectError)
      .then(this.updateWSToken.bind(this))
      .then(res => res.json())
  }

  public newProject(user: string, title: string): Promise<Project> {
    if (user.length > 0 && title.length > 0) {
      return fetch(this.projectUri(), {
        method: 'POST',
        headers: {
          [authHeader]: this.token(),
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ owner: user, name: title })
      })
        .then(rejectError)
        .then(this.updateWSToken.bind(this))
        .then(res => res.json())
    }
    else
      return Promise.reject("username & title must be provided and not empty!")
  }

  public newSession(project: Project): Promise<Session> {
    return fetch(this.projectUri() + `/${project.id}/sessions/new`, {
      method: 'POST',
      headers: {
        [authHeader]: this.token(),
        'Accept': 'application/json'
      }
    })
      .then(rejectError)
      .then(this.updateWSToken.bind(this))
      .then(res => res.json())
      .then((obj: any) => ({ ...obj, compilerErrors: [] }))
  }

  public compile(file: File): Promise<CompilerError[]> {
    const session = this.store.getState().session
    if (session) {
      return fetch(this.sessionUri() + `/${session.id}/compile`, {
        method: 'POST',
        headers: {
          [authHeader]: this.token(),
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify({ path: file.relativePath })
      }).then(rejectError)
        .then(this.updateWSToken.bind(this))
        .then(res => res.json())
    } else {
      return Promise.reject("can't compile if there is no session!")
    }
  }

  public updateFile(file: File): Promise<File> {
    const session = this.store.getState().session
    if (session) {
      return fetch(this.sessionUri() + `/${session.id}/files/update`, {
        method: 'POST',
        headers: {
          [authHeader]: this.token(),
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify(file)
      })
        .then(rejectError)
        .then(this.updateWSToken.bind(this))
        .then(res => file)
    } else {
      return Promise.reject("can't create a file if there is no session!")
    }
  }

  public simulate(r:SimulateRequest): Promise<string> {
    const session = this.store.getState().session
    if (session) {
      return fetch(this.sessionUri() + `/${session.id}/simulate`, {
        method: 'POST',
        headers: {
          [authHeader]: this.token(),
          'Accept': 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(r)
      })
        .then(rejectError)
        .then(this.updateWSToken.bind(this))
        .then(res => res.headers.get("Location")!)
    } else {
      return Promise.reject("can't simulate without a session!")
    }
  }

  public getSimulationResults(addr: string, format: string = "chartjs"): Promise<SimulationResult | TableFormat> {
    //TODO: addr is a CORS request because it's at :8888 instead of expected :3000
    // fix this???!
    const session = this.store.getState().session
    if (session) {
      return fetch(addr, {
        method: 'GET',
        headers: {
          [authHeader]: this.token(),
          'Accept': 'application/json'
        }
      })
        .then(rejectError)
        .then(this.updateWSToken.bind(this))
        .then(res => res.json())
    } else {
      return Promise.reject("can't simulate without a session!")
    }
  }
}
