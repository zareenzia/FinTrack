from flask import Flask, render_template, request, jsonify
from flask_cors import CORS
from flask_sqlalchemy import SQLAlchemy
from datetime import datetime
import os
from dotenv import load_dotenv

load_dotenv()

app = Flask(__name__)
CORS(app)

# Configuration
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///fintrack.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)

# Database Models
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

# Routes - Pages
@app.route('/')
def index():
    return render_template('login.html')

@app.route('/dashboard')
def dashboard():
    return render_template('dashboard.html')

@app.route('/transactions')
def transactions_page():
    return render_template('transactions.html')

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
