from flask import Flask, render_template, request, jsonify, session, redirect, url_for, g
from flask_cors import CORS
from flask_sqlalchemy import SQLAlchemy
from datetime import datetime
import os
import re
import uuid
from functools import wraps
from dotenv import load_dotenv
from werkzeug.security import generate_password_hash, check_password_hash
from werkzeug.utils import secure_filename

load_dotenv()

app = Flask(__name__)
CORS(app)

# Configuration
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///fintrack.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.secret_key = os.environ.get('SECRET_KEY', 'fintrack-dev-secret-key-2024-change-in-prod')

UPLOAD_FOLDER = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'static', 'uploads', 'profiles')
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['MAX_CONTENT_LENGTH'] = 5 * 1024 * 1024  # 5 MB
ALLOWED_EXTENSIONS = {'jpg', 'jpeg', 'png', 'webp'}

os.makedirs(UPLOAD_FOLDER, exist_ok=True)

db = SQLAlchemy(app)

# ─── Helpers ──────────────────────────────────────────────────────────────────

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS


def validate_username(username):
    """Returns an error string, or None if valid."""
    if not username:
        return None
    username = username.strip()
    if len(username) < 3:
        return 'Username must be at least 3 characters'
    if len(username) > 30:
        return 'Username must not exceed 30 characters'
    if not re.match(r'^[a-zA-Z0-9_.]+$', username):
        return 'Username can only contain letters, numbers, underscores (_), and periods (.)'
    return None


def validate_password_strength(password):
    """Returns an error string, or None if the password meets policy."""
    if len(password) < 8:
        return 'Password must be at least 8 characters'
    if not re.search(r'[A-Z]', password):
        return 'Password must contain at least one uppercase letter'
    if not re.search(r'[a-z]', password):
        return 'Password must contain at least one lowercase letter'
    if not re.search(r'\d', password):
        return 'Password must contain at least one number'
    if not re.search(r'[!@#$%^&*()\-_=+\[\]{};:\'",.<>/?\\|`~]', password):
        return 'Password must contain at least one special character'
    return None


def login_required_api(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if 'user_id' not in session:
            return jsonify({'error': 'Authentication required'}), 401
        return f(*args, **kwargs)
    return decorated


def login_required_page(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if 'user_id' not in session:
            return redirect(url_for('index'))
        return f(*args, **kwargs)
    return decorated


# ─── Database Models ──────────────────────────────────────────────────────────

class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    full_name = db.Column(db.String(100), nullable=False)
    username = db.Column(db.String(30), unique=True, nullable=True, index=True)
    email = db.Column(db.String(120), unique=True, nullable=False, index=True)
    password_hash = db.Column(db.String(256), nullable=False)
    profile_picture = db.Column(db.String(255), nullable=True)
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)
    updated_at = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)

    def set_password(self, password):
        self.password_hash = generate_password_hash(password)

    def check_password(self, password):
        return check_password_hash(self.password_hash, password)

    def to_dict(self):
        pic_url = None
        if self.profile_picture:
            pic_url = url_for('static', filename=f'uploads/profiles/{self.profile_picture}')
        return {
            'id': self.id,
            'full_name': self.full_name,
            'username': self.username,
            'email': self.email,
            'profile_picture': pic_url,
            'created_at': self.created_at.isoformat(),
        }


class Category(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(100), nullable=False, unique=True)
    description = db.Column(db.String(255))
    color = db.Column(db.String(7), default='#3498db')
    icon = db.Column(db.String(50), default='tag')
    transactions = db.relationship('Transaction', backref='category', lazy=True, cascade='all, delete-orphan')

    def to_dict(self):
        return {
            'id': self.id,
            'name': self.name,
            'description': self.description,
            'color': self.color,
            'icon': self.icon
        }

class Transaction(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    amount = db.Column(db.Float, nullable=False)
    description = db.Column(db.String(255), nullable=False)
    category_id = db.Column(db.Integer, db.ForeignKey('category.id'), nullable=False)
    transaction_type = db.Column(db.String(10), nullable=False)  # 'income' or 'expense'
    date = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)

    def to_dict(self):
        return {
            'id': self.id,
            'amount': self.amount,
            'description': self.description,
            'category_id': self.category_id,
            'category_name': self.category.name if self.category else None,
            'transaction_type': self.transaction_type,
            'date': self.date.isoformat(),
            'created_at': self.created_at.isoformat()
        }


# ─── Before-request & context processor (after models are defined) ────────────

@app.before_request
def load_logged_in_user():
    user_id = session.get('user_id')
    g.user = db.session.get(User, user_id) if user_id else None


@app.context_processor
def inject_user():
    return dict(current_user=g.user)


# ─── Auth Routes ──────────────────────────────────────────────────────────────

@app.route('/auth/register', methods=['POST'])
def register():
    data = request.get_json()
    if not data:
        return jsonify({'error': 'Invalid request'}), 400

    full_name = (data.get('full_name') or '').strip()
    email = (data.get('email') or '').strip().lower()
    password = data.get('password') or ''

    if not full_name:
        return jsonify({'error': 'Full name is required'}), 400
    if len(full_name) > 100:
        return jsonify({'error': 'Full name is too long (max 100 characters)'}), 400
    if not email:
        return jsonify({'error': 'Email is required'}), 400
    if not re.match(r'^[^\s@]+@[^\s@]+\.[^\s@]+$', email):
        return jsonify({'error': 'Invalid email address'}), 400

    pwd_error = validate_password_strength(password)
    if pwd_error:
        return jsonify({'error': pwd_error}), 400

    if User.query.filter(db.func.lower(User.email) == email).first():
        return jsonify({'error': 'An account with this email already exists'}), 409

    user = User(full_name=full_name, email=email)
    user.set_password(password)
    db.session.add(user)
    db.session.commit()

    session['user_id'] = user.id
    return jsonify({'message': 'Account created successfully', 'user': user.to_dict()}), 201


@app.route('/auth/login', methods=['POST'])
def login():
    data = request.get_json()
    if not data:
        return jsonify({'error': 'Invalid request'}), 400

    identifier = (data.get('identifier') or '').strip().lower()
    password = data.get('password') or ''

    if not identifier or not password:
        return jsonify({'error': 'Email/username and password are required'}), 400

    user = User.query.filter(db.func.lower(User.email) == identifier).first()
    if not user:
        user = User.query.filter(db.func.lower(User.username) == identifier).first()

    if not user or not user.check_password(password):
        return jsonify({'error': 'Invalid credentials. Please check your email/username and password.'}), 401

    session.permanent = bool(data.get('remember', False))
    session['user_id'] = user.id
    return jsonify({'message': 'Logged in successfully', 'user': user.to_dict()})


@app.route('/auth/logout', methods=['POST'])
def logout():
    session.clear()
    return jsonify({'message': 'Logged out successfully'})


@app.route('/api/auth/status')
def auth_status():
    if g.user:
        return jsonify({'authenticated': True, 'user': g.user.to_dict()})
    return jsonify({'authenticated': False})


# ─── Profile Routes ───────────────────────────────────────────────────────────

@app.route('/api/profile', methods=['GET'])
@login_required_api
def get_profile():
    return jsonify(g.user.to_dict())


@app.route('/api/profile', methods=['PUT'])
@login_required_api
def update_profile():
    data = request.get_json()
    if not data:
        return jsonify({'error': 'Invalid request'}), 400

    user = g.user

    if 'full_name' in data:
        full_name = (data['full_name'] or '').strip()
        if not full_name:
            return jsonify({'error': 'Full name cannot be empty'}), 400
        if len(full_name) > 100:
            return jsonify({'error': 'Full name is too long (max 100 characters)'}), 400
        user.full_name = full_name

    if 'username' in data:
        username = (data['username'] or '').strip()
        if username == '':
            user.username = None
        else:
            error = validate_username(username)
            if error:
                return jsonify({'error': error}), 400
            existing = User.query.filter(
                db.func.lower(User.username) == username.lower(),
                User.id != user.id
            ).first()
            if existing:
                return jsonify({'error': 'Username is already taken'}), 409
            user.username = username

    user.updated_at = datetime.utcnow()
    db.session.commit()
    return jsonify({'message': 'Profile updated successfully', 'user': user.to_dict()})


@app.route('/api/profile/picture', methods=['POST'])
@login_required_api
def upload_profile_picture():
    if 'picture' not in request.files:
        return jsonify({'error': 'No file provided'}), 400

    file = request.files['picture']
    if not file or file.filename == '':
        return jsonify({'error': 'No file selected'}), 400

    if not allowed_file(file.filename):
        return jsonify({'error': 'Unsupported file type. Use JPG, JPEG, PNG, or WebP'}), 400

    user = g.user
    if user.profile_picture:
        old_path = os.path.join(app.config['UPLOAD_FOLDER'], user.profile_picture)
        if os.path.exists(old_path):
            os.remove(old_path)

    ext = file.filename.rsplit('.', 1)[1].lower()
    filename = f'{uuid.uuid4().hex}.{ext}'
    file.save(os.path.join(app.config['UPLOAD_FOLDER'], filename))

    user.profile_picture = filename
    user.updated_at = datetime.utcnow()
    db.session.commit()

    return jsonify({
        'message': 'Profile picture updated',
        'profile_picture': url_for('static', filename=f'uploads/profiles/{filename}')
    })


@app.route('/api/profile/picture', methods=['DELETE'])
@login_required_api
def delete_profile_picture():
    user = g.user
    if not user.profile_picture:
        return jsonify({'error': 'No profile picture to remove'}), 404

    file_path = os.path.join(app.config['UPLOAD_FOLDER'], user.profile_picture)
    if os.path.exists(file_path):
        os.remove(file_path)

    user.profile_picture = None
    user.updated_at = datetime.utcnow()
    db.session.commit()
    return jsonify({'message': 'Profile picture removed'})


@app.route('/api/profile/change-password', methods=['POST'])
@login_required_api
def change_password():
    data = request.get_json()
    if not data:
        return jsonify({'error': 'Invalid request'}), 400

    current_password = data.get('current_password') or ''
    new_password = data.get('new_password') or ''
    confirm_password = data.get('confirm_password') or ''

    if not current_password:
        return jsonify({'error': 'Current password is required'}), 400

    user = g.user
    if not user.check_password(current_password):
        return jsonify({'error': 'Current password is incorrect'}), 401

    if not new_password:
        return jsonify({'error': 'New password is required'}), 400

    error = validate_password_strength(new_password)
    if error:
        return jsonify({'error': error}), 400

    if new_password != confirm_password:
        return jsonify({'error': 'New password and confirmation do not match'}), 400

    if current_password == new_password:
        return jsonify({'error': 'New password must be different from the current password'}), 400

    user.set_password(new_password)
    user.updated_at = datetime.utcnow()
    db.session.commit()
    return jsonify({'message': 'Password changed successfully'})


@app.route('/api/profile/check-username')
@login_required_api
def check_username():
    username = request.args.get('username', '').strip()
    if not username:
        return jsonify({'available': False, 'error': 'Username is required'})

    error = validate_username(username)
    if error:
        return jsonify({'available': False, 'error': error})

    existing = User.query.filter(
        db.func.lower(User.username) == username.lower(),
        User.id != g.user.id
    ).first()
    if existing:
        return jsonify({'available': False, 'error': 'Username is already taken'})

    return jsonify({'available': True, 'message': 'Username is available'})


# ─── Page Routes ──────────────────────────────────────────────────────────────

@app.route('/')
def index():
    if g.user:
        return redirect(url_for('dashboard'))
    return render_template('login.html')

@app.route('/dashboard')
@login_required_page
def dashboard():
    return render_template('dashboard.html')

@app.route('/transactions')
@login_required_page
def transactions_page():
    return render_template('transactions.html')

@app.route('/settings')
@login_required_page
def settings_page():
    return render_template('settings.html')

# API Routes - Categories
@app.route('/api/categories', methods=['GET'])
def get_categories():
    categories = Category.query.all()
    return jsonify([cat.to_dict() for cat in categories])

@app.route('/api/categories', methods=['POST'])
def create_category():
    data = request.get_json()
    if not data or not data.get('name'):
        return jsonify({'error': 'Category name is required'}), 400
    
    category = Category(
        name=data['name'],
        description=data.get('description', ''),
        color=data.get('color', '#3498db'),
        icon=data.get('icon', 'tag')
    )
    db.session.add(category)
    db.session.commit()
    return jsonify(category.to_dict()), 201

@app.route('/api/categories/<int:id>', methods=['GET'])
def get_category(id):
    category = Category.query.get_or_404(id)
    return jsonify(category.to_dict())

@app.route('/api/categories/<int:id>', methods=['PUT'])
def update_category(id):
    category = Category.query.get_or_404(id)
    data = request.get_json()
    
    if 'name' in data:
        category.name = data['name']
    if 'description' in data:
        category.description = data['description']
    if 'color' in data:
        category.color = data['color']
    if 'icon' in data:
        category.icon = data['icon']
    
    db.session.commit()
    return jsonify(category.to_dict())

@app.route('/api/categories/<int:id>', methods=['DELETE'])
def delete_category(id):
    category = Category.query.get_or_404(id)
    db.session.delete(category)
    db.session.commit()
    return '', 204

# API Routes - Transactions
@app.route('/api/transactions', methods=['GET'])
def get_transactions():
    category_id = request.args.get('category_id', type=int)
    transaction_type = request.args.get('type')
    limit = request.args.get('limit', 100, type=int)
    
    query = Transaction.query.order_by(Transaction.date.desc())
    
    if category_id:
        query = query.filter_by(category_id=category_id)
    if transaction_type:
        query = query.filter_by(transaction_type=transaction_type)
    
    transactions = query.limit(limit).all()
    return jsonify([tx.to_dict() for tx in transactions])

@app.route('/api/transactions', methods=['POST'])
def create_transaction():
    data = request.get_json()
    
    required_fields = ['amount', 'description', 'category_id', 'transaction_type']
    if not all(field in data for field in required_fields):
        return jsonify({'error': 'Missing required fields'}), 400
    
    transaction = Transaction(
        amount=data['amount'],
        description=data['description'],
        category_id=data['category_id'],
        transaction_type=data['transaction_type'],
        date=datetime.fromisoformat(data.get('date', datetime.utcnow().isoformat()))
    )
    db.session.add(transaction)
    db.session.commit()
    return jsonify(transaction.to_dict()), 201

@app.route('/api/transactions/<int:id>', methods=['GET'])
def get_transaction(id):
    transaction = Transaction.query.get_or_404(id)
    return jsonify(transaction.to_dict())

@app.route('/api/transactions/<int:id>', methods=['PUT'])
def update_transaction(id):
    transaction = Transaction.query.get_or_404(id)
    data = request.get_json()
    
    if 'amount' in data:
        transaction.amount = data['amount']
    if 'description' in data:
        transaction.description = data['description']
    if 'category_id' in data:
        transaction.category_id = data['category_id']
    if 'transaction_type' in data:
        transaction.transaction_type = data['transaction_type']
    if 'date' in data:
        transaction.date = datetime.fromisoformat(data['date'])
    
    db.session.commit()
    return jsonify(transaction.to_dict())

@app.route('/api/transactions/<int:id>', methods=['DELETE'])
def delete_transaction(id):
    transaction = Transaction.query.get_or_404(id)
    db.session.delete(transaction)
    db.session.commit()
    return '', 204

# API Routes - Analytics
@app.route('/api/analytics/summary', methods=['GET'])
def get_summary():
    total_income = db.session.query(db.func.sum(Transaction.amount)).filter_by(transaction_type='income').scalar() or 0
    total_expense = db.session.query(db.func.sum(Transaction.amount)).filter_by(transaction_type='expense').scalar() or 0
    balance = total_income - total_expense
    
    return jsonify({
        'total_income': total_income,
        'total_expense': total_expense,
        'balance': balance,
        'transaction_count': Transaction.query.count()
    })

@app.route('/api/analytics/category-breakdown', methods=['GET'])
def get_category_breakdown():
    breakdown = db.session.query(
        Category.name,
        Category.color,
        db.func.sum(Transaction.amount).label('total'),
        db.func.count(Transaction.id).label('count')
    ).join(Transaction).filter_by(transaction_type='expense').group_by(Category.id).all()
    
    return jsonify([{
        'category': row[0],
        'color': row[1],
        'total': row[2],
        'count': row[3]
    } for row in breakdown])

@app.route('/api/analytics/monthly', methods=['GET'])
def get_monthly_analytics():
    from sqlalchemy import func
    import calendar
    
    result = db.session.query(
        func.strftime('%Y-%m', Transaction.date).label('month'),
        Transaction.transaction_type,
        func.sum(Transaction.amount).label('total')
    ).group_by('month', Transaction.transaction_type).order_by('month').all()
    
    return jsonify([{
        'month': row[0],
        'type': row[1],
        'total': row[2]
    } for row in result])

# Error handlers
@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Not found'}), 404

@app.errorhandler(500)
def internal_error(error):
    db.session.rollback()
    return jsonify({'error': 'Internal server error'}), 500

if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(debug=True, host='0.0.0.0', port=5000)
