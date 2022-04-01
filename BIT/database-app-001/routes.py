"""Module for all the routes in database app"""
import os
from urllib.parse import urlparse, urljoin
from flask import flash, redirect, render_template, request, jsonify
import flask
from sqlalchemy import create_engine

# re
import re

# flask plugins
from flask_login import login_user, login_required, logout_user, current_user
from flask_cors import cross_origin

# user authentication
from werkzeug.security import check_password_hash

# forms
from flask_wtf import FlaskForm
from wtforms import StringField, PasswordField, SubmitField

# user classes
from questions import Questions
from question import Question
from user import User
from database import  get_correct_answers, create_user, get_questions_with_answer_count,\
                      get_answers, get_quiz_name, get_quiz_id_by_name, get_all_scores, get_scores,\
                      get_username_by_id, add_question, add_quiz, add_answer

from app import app, login_manager

# print(os.environ)
engine = create_engine(os.getenv("DATABASE_URL"))

# LoginForm class


class LoginForm(FlaskForm):
    """LoginForm will rendered in loginpage router"""
    username = StringField('Username')
    password = PasswordField('Password')
    submit = SubmitField('Login')

# login


@app.route('/loginpage', methods=['GET', 'POST'])
def login_page():
    """Login page router, user will login on this page"""
    return flask.render_template('login.html')


@app.route('/login', methods=['GET', 'POST'])
def login():
    """Login router, form data will be sent to it"""
    # authentication
    username = request.form.get('username')
    password = request.form.get('password')

    if len(username) < 3:
        flash('username is too short', 'info')
        return redirect('/loginpage')
    
    if not re.fullmatch(r"^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!#%*?&]{6,20}$", password):
        flash('invalid password', 'info')
        flash('Password must be 6-20 characters long, at least one letter, one number and one special character.', 'info')
        return redirect('/loginpage')

    db_user = engine.execute(
        "select id, username from users where username=%(username)s;", {"username":username} ).fetchone()
    password_hash = engine.execute(
        "SELECT password FROM users WHERE username=%(username)s;", {"username": username}).fetchone()

    if not db_user:
        print('creating new user', username, password)
        flash(f"new user created: {username}", 'success')
        user = create_user(username, password)
        login_user(user)
        return redirect('/')

    if not check_password_hash(password_hash[0], password):
        flash('Username and password do not match', 'danger')
        return redirect('/loginpage')

    user = User(db_user[1], db_user[0])
    login_user(user)
    return redirect('/')


@app.route("/logout")
@login_required
def logout():
    """Logs user out"""
    print(current_user.is_authenticated)
    logout_user()
    flash('logged out successfully', 'info')
    return redirect('/')

@app.route("/new_quiz")
def new_quiz():
    if not current_user.is_authenticated:
        flash('Login before creating quizzes!', 'info')
        return redirect('/loginpage')
    
    return render_template('new_quiz.html')

@app.route("/add_new_quiz", methods=["POST"])
def add_new_quiz():
    body = request.form
    quiz_id = add_quiz(body['name'], body['description'])
    current_question_id = 0

    print('\n\n\n\n\n')
    for item in body:
        print(item, '=', body[item])
    print('\n\n\n\n\n')

    loop_idx = 0
    latest_answer = ''
    for item in body:
        loop_idx += 1
        if loop_idx < 3:
            continue
        if item[0] == 'q':
            current_question_id = add_question(quiz_id, body[item])
        elif item[0] == 'a':
            latest_answer = body[item]
        else:
            if body[item] == 'True':
                is_correct = 1
            else:
                print('tiem: ', item, 'body[item]', body[item])
                is_correct = 0
            add_answer(current_question_id, latest_answer, is_correct)

    return redirect('/')



def is_safe_url(target):
    """Checks if url is safe"""
    ref_url = urlparse(request.host_url)
    test_url = urlparse(urljoin(request.host_url, target))
    return test_url.scheme in ('http', 'https') and \
        ref_url.netloc == test_url.netloc


@login_manager.user_loader
def load_user(user_id):
    """Loads user from database with login-flask UserMixin function"""
    return User.get(user_id)


# general

@app.route("/")
def index():
    """Index"""
    quizes = engine.execute('select * from quizes;').fetchall()
    print('-----------------', quizes)
    return render_template("frontpage.html", quizes=quizes,
                           len=len(quizes), current_user=current_user)


@app.route('/quiz/<string:quiz_url>')
def quiz(quiz_url):
    """quiz router"""
    if not current_user.is_authenticated:
        flash('Login before completing quizzes!', 'info')
        return redirect('/loginpage')

    quizname = engine.execute(f"select name from quizes where url='{quiz_url}';").fetchone()[0]
    questions = get_questions_with_answer_count(quizname)
    answers = get_answers(quizname)

    if len(questions) == 0:
        return render_template('quiz_not_ready.html', quizname=quiz_url)

    print(questions)
    print(answers)
    # qna will be and array of arrays, each sub array represents a question
    # and its answers, each node in string format
    qna = []

    # for each question, add the question_string into an array, then append that array
    # with the answers that have the appropriate question id
    for ques in questions:
        arr = [ques[0]]
        for ans in answers:
            if ques[1] == ans[1]:
                arr.append(ans[0])
        qna.append(arr)

    return render_template(
        'quiz.html',
        quizname=quiz_url,
        qna=qna,
        questions=questions,
        answers=answers,
        answerslen=len(answers),
        questionslen=len(questions),
        submit=flask.url_for('submit'))


@app.route('/quiz/results/<string:result_id>')
def result(result_id):
    """results for given game id"""
    score = engine.execute(
        f'select score, quiz_id from scores where id={result_id};').fetchone()
    print('scoreeeeeeeeeeeeeeeeeee', score)
    name = get_quiz_name(score[1])
    return render_template('results.html', score=score[0], name=name)


@app.route('/quiz/submit', methods=['POST', 'GET'])
@cross_origin()
def submit():
    """Submits results and redirects to result page"""
    body = request.form
    quiz_url = body['quizname']
    quizname = engine.execute(f"select name from quizes where url='{quiz_url}'").fetchone()[0]
    questions_answers = get_correct_answers(quizname)

    # Add questions and correct answers to an object that is easily querable
    questions_ = Questions()
    for qna in questions_answers:
        ques = Question(qna[0])
        ques.add_answer(qna[1])
        questions_.add_question(ques)

    # Create an array of type [(question, given answer, correct or not)]
    result_list = []
    answer_list = []
    score = 0
    print()
    print()
    print()
    print()
    print()
    print('questions_answers', questions_answers)
    for question_ in questions_answers:
        ques = question_[0]  # question
        ans = body[ques]     # answer
        # Duplicate answers sometimes result from multiple correct answers,
        # skip them via this statement
        if [ques, ans] in answer_list:
            continue

        answer_list.append([ques, ans])

        if questions_.is_correct(ques, ans):
            result_list.append((ques, ans, True))
            score = score + 1
        else:
            result_list.append((ques, ans, False))

    quiz_id = get_quiz_id_by_name(quizname)

    # generate id for this quiz session
    response = engine.execute(
        f"INSERT INTO scores (score, quiz_id, user_id) \
          VALUES ({score}, {quiz_id}, {current_user.get_id()}) RETURNING id;")

    print('scores after insertr', engine.execute(
        'select * from scores;').fetchall())
    # engine.commit()
    response = jsonify(id=response.fetchone()[0])
    response.headers.add('Access-Control-Allow-Origin', '*')
    return response


@app.route('/profile')
def profile():
    """Profile page"""
    if not current_user.is_authenticated:
        return redirect('/loginpage')

    scores = get_scores(current_user.get_id())

    quiz_names_and_scores = []
    for score in scores:
        name = get_quiz_name(score[3])
        quiz_names_and_scores.append([name, score[2]])

        print(quiz_names_and_scores)

    return render_template(
        'profile.html',
        current_user=current_user,
        scores=quiz_names_and_scores)


@app.route('/highscores')
def highscores():
    """Highscore page"""
    scores = get_all_scores()
    quiz_user_score = []
    for score in scores:
        if score[0]:
            username = get_username_by_id(score[0])
            quizname = get_quiz_name(score[1])
            quiz_user_score.append([quizname, username, score[2]])

    print(quiz_user_score)

    return render_template('highscores.html', scores=quiz_user_score)
